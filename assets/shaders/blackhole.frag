// Schwarzschild black hole shader for Mindustry
// Adapted from Ghostty blackhole_preview.glsl / blackhole.hlsl
// Renders a gravitational-lens black hole with accretion disk over a screen texture.

#define HIGHP

uniform sampler2D u_texture;

uniform vec2 u_resolution;
uniform float u_time;
uniform vec2 u_center;      // black hole center in screen UV [0,1]
uniform float u_holeRadius; // normalized hole radius (default ~0.025)
uniform float u_exposure;   // tone-mapping exposure

// Accretion disk look
uniform float u_diskTemp;
uniform float u_diskIncl;
uniform float u_diskRoll;
uniform float u_diskInner;
uniform float u_diskOuter;
uniform float u_diskOpac;
uniform float u_diskDopp;
uniform float u_diskBeam;
uniform float u_diskGain;
uniform float u_diskContr;
uniform float u_diskWind;
uniform float u_diskSpeed;
uniform float u_diskStar;

varying vec2 v_texCoord;

// ── Tunable defaults ──
#define HOLE_RADIUS   0.0200
#define LENS_DEPTH    13.0000
#define DISK_INNER    1.8000
#define DISK_OUTER    8.0000
#define DISK_INCL     1.5000
#define DISK_ROLL     0.3500
#define DISK_GAIN     2.2000
#define DISK_OPACITY  0.9000
#define DISK_TEMP     5500.0000
#define DOPPLER_MIX   0.6000
#define DISK_BEAM     2.5000
#define DISK_SPEED    5.0000
#define DISK_WIND     7.0000
#define DISK_CONTRAST 1.6000
#define EXPOSURE      1.4000
#define DILATION_MIN  0.2000
#define B_CRIT        2.5980762
#define N_STEPS       32

#define useHoleR      (u_holeRadius > 0.0 ? u_holeRadius : HOLE_RADIUS)

// ── Hash ──
float hash21(vec2 p){
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

// ── Value noise with Y wrapping ──
float vnoiseWrapY(vec2 p, float perY){
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float y0 = mod(i.y, perY);
    float y1 = mod(i.y + 1.0, perY);
    return mix(
        mix(hash21(vec2(i.x, y0)), hash21(vec2(i.x + 1.0, y0)), f.x),
        mix(hash21(vec2(i.x, y1)), hash21(vec2(i.x + 1.0, y1)), f.x),
        f.y);
}

// ── Mirror UV ──
vec2 mirrorUV(vec2 u){
    return 1.0 - abs(1.0 - mod(u, 2.0));
}

// ── 2D Rotation ──
vec2 rot2(vec2 v, float a){
    float c = cos(a), s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// ── Blackbody color (Tanner Helland fit) ──
vec3 blackbody(float T){
    float t = clamp(T, 1500.0, 40000.0) / 100.0;
    float r = t <= 66.0 ? 1.0
                        : clamp(1.292936 * pow(t - 60.0, -0.1332047), 0.0, 1.0);
    float g = t <= 66.0 ? clamp(0.3900816 * log(t) - 0.6318414, 0.0, 1.0)
                        : clamp(1.1298909 * pow(t - 60.0, -0.0755148), 0.0, 1.0);
    float b = t >= 66.0 ? 1.0
                        : (t <= 19.0 ? 0.0
                                     : clamp(0.5432068 * log(t - 10.0) - 1.1962540, 0.0, 1.0));
    return vec3(r, g, b);
}

// ── Procedural starfield ──
vec3 stars(vec3 d){
    vec2 sph = vec2(atan(d.x, -d.z), asin(clamp(d.y, -1.0, 1.0)));
    vec2 g   = sph * 40.0;
    vec2 id  = floor(g);
    float h  = hash21(id);
    if(h < 0.92) return vec3(0.0);
    vec2 f   = fract(g) - 0.5;
    vec2 off = (vec2(hash21(id + 17.3), hash21(id + 31.7)) - 0.5) * 0.7;
    float spark = smoothstep(0.10, 0.0, length(f - off));
    float tw    = 0.7 + 0.3 * sin(u_time * (0.5 + 2.0 * hash21(id + 5.1)) + 40.0 * h);
    vec3 tint   = mix(vec3(1.0, 0.82, 0.60), vec3(0.75, 0.85, 1.0), hash21(id + 2.9));
    return tint * spark * tw * ((h - 0.92) / 0.08);
}

void main(){
    vec2 res    = u_resolution.xy;
    vec2 uv     = v_texCoord;
    float aspect = res.x / res.y;
    float t     = u_time;

    float rin  = max(u_diskInner > 0.0 ? u_diskInner : DISK_INNER, 1.6);
    float rout = max(u_diskOuter > 0.0 ? u_diskOuter : DISK_OUTER, rin + 0.5);
    float rh   = useHoleR;
    float W    = B_CRIT / max(rh, 1e-4);
    float I    = 1.0;
    float dil  = mix(1.0, DILATION_MIN, I);
    float vis  = 1.0;

    float incl = u_diskIncl > 0.0 ? u_diskIncl : DISK_INCL;
    float roll = u_diskRoll;
    float opac = u_diskOpac  > 0.0 ? u_diskOpac  : DISK_OPACITY;
    float dopp = u_diskDopp  > 0.0 ? u_diskDopp  : DOPPLER_MIX;
    float beam = u_diskBeam  > 0.0 ? u_diskBeam  : DISK_BEAM;
    float gain = u_diskGain  > 0.0 ? u_diskGain  : DISK_GAIN;
    float contr= u_diskContr > 0.0 ? u_diskContr : DISK_CONTRAST;
    float wind = u_diskWind  > 0.0 ? u_diskWind  : DISK_WIND;
    float spd  = u_diskSpeed > 0.0 ? u_diskSpeed : DISK_SPEED;
    float temp = u_diskTemp  > 0.0 ? u_diskTemp  : DISK_TEMP;
    float star = u_diskStar;
    float expo = u_exposure  > 0.0 ? u_exposure  : EXPOSURE;

    vec2 center = u_center;
    float shield = vis * smoothstep(0.0, 0.18, uv.y);

    vec2 p     = (uv - center) * vec2(aspect, 1.0);
    float plen = length(p);
    vec2 pr    = rot2(p, roll) * W;
    float b    = length(pr);
    float window = exp(-pow(plen / (7.0 * rh), 2.0));
    float bmax = rout + 3.0;
    float Z0   = max(14.0, rout + 5.0);

    // ═══ Far field: weak deflection ═══
    if(b >= bmax){
        float u    = Z0 * inversesqrt(Z0 * Z0 + b * b);
        float defl = (2.0 / (W * W)) / max(plen, 1e-4)
                   * (1.29 * u + 0.07) * max(LENS_DEPTH - 2.14 * u + 0.75, 0.0)
                   * window * shield;
        vec2 dir = p / max(plen, 1e-5);
        vec3 term = vec3(0.0);
        float ab = 0.035 * smoothstep(1.0, 2.0, b / bmax);
        for(int i = 0; i < 3; i++){
            float k   = 1.0 + (float(i) - 1.0) * ab;
            vec2 sp   = p - dir * defl * k;
            vec2 suv  = mirrorUV(center + sp / vec2(aspect, 1.0));
            term[i]   = texture2D(u_texture, suv)[i];
        }
        vec3 d = normalize(vec3(-(pr / b) * (2.0 / b), -1.0));
        gl_FragColor = vec4(term + stars(d) * star * window * shield, 1.0);
        return;
    }

    // ═══ Near field: trace geodesic ═══
    vec3 x  = vec3(pr, Z0);
    vec3 v  = vec3(0.0, 0.0, -1.0);
    float h2 = dot(pr, pr);
    float ci = cos(incl), si = sin(incl);
    vec3 n   = vec3(0.0, si, ci);
    vec3 e2  = vec3(0.0, ci, -si);
    float sdir = spd < 0.0 ? -1.0 : 1.0;
    float spda = abs(spd);

    vec3 emitc    = vec3(0.0);
    float trans   = 1.0;
    bool captured = false;
    float sPrev   = dot(x, n);
    vec3 xPrev    = x;

    for(int i = 0; i < N_STEPS; i++){
        float r2 = dot(x, x);
        if(r2 < 1.0){ captured = true; break; }
        if(x.z < -Z0 && v.z < 0.0) break;
        if(r2 > 4.0 * Z0 * Z0) break;
        float r  = sqrt(r2);
        float dt = clamp(0.16 * r, 0.03, 1.5);

        vec3 a = -1.5 * h2 * x / (r2 * r2 * r);
        v += a * (0.5 * dt);
        x += v * dt;
        r2 = dot(x, x);
        r  = sqrt(r2);
        a  = -1.5 * h2 * x / (r2 * r2 * r);
        v += a * (0.5 * dt);

        float s = dot(x, n);
        if(s * sPrev < 0.0 && trans > 0.02){
            float tc = sPrev / (sPrev - s);
            vec3 xc  = mix(xPrev, x, tc);
            float rc = length(xc);
            if(rc > rin && rc < rout){
                float band = smoothstep(rin, rin * 1.25, rc)
                           * (1.0 - smoothstep(rout * 0.70, rout, rc));
                float phi   = atan(dot(xc, e2), xc.x);
                float turns = phi / 6.2831853;
                float kep   = pow(rin / rc, 1.5);
                float gloc  = sqrt(max(1.0 - 1.5 / rc, 0.02));
                float swirl = rc * wind * 0.12 - t * kep * spda * gloc * dil * sdir;
                float streaks = vnoiseWrapY(vec2(rc * 2.8, turns * 19.0 + swirl * 3.0), 19.0) * 0.65
                              + vnoiseWrapY(vec2(rc * 1.0, turns * 9.0  + swirl * 1.5 + 7.0), 9.0) * 0.35;
                streaks = 0.35 + contr * streaks * streaks;

                vec3 gasdir = normalize(cross(n, xc)) * sdir;
                float beta  = clamp(inversesqrt(max(2.0 * (rc - 1.0), 0.2)), 0.0, 0.99);
                float g     = gloc / max(1.0 + beta * dot(gasdir, normalize(v)), 0.05);
                g = mix(1.0, g, dopp);

                float xpr   = max(1.0 - sqrt(rin / rc), 0.0);
                float tprof = pow(rin / rc, 0.75) * pow(xpr, 0.25) / 0.488;
                vec3 cbb    = blackbody(temp * tprof * g);
                float boost = pow(g, beam);

                float density = band * streaks;
                emitc += trans * cbb * (gain * 2.2 * density * tprof * tprof * boost);
                trans *= 1.0 - clamp(opac * density, 0.0, 1.0);
            }
        }
        sPrev = s;
        xPrev = x;
    }
    if(!captured && dot(x, x) < 4.0) captured = true;

    // ── Background ──
    vec3 bg = vec3(0.0);
    if(!captured){
        vec3 d = normalize(v);
        bg += stars(d) * star * window * shield;
        if(d.z < -0.05){
            float tpl = (-LENS_DEPTH - x.z) / d.z;
            vec3 hp = x + d * tpl;
            vec2 q  = rot2(hp.xy, -roll) / W;
            vec2 sp = vec2(q.x, -q.y);
            vec2 suv = mirrorUV(center + (p + (sp - p) * window * shield) / vec2(aspect, 1.0));
            float toward = smoothstep(0.05, 0.35, -d.z);
            bg += texture2D(u_texture, suv).rgb * toward;
        }
    }

    vec3 col = bg * trans + (vec3(1.0) - exp(-emitc * expo));
    gl_FragColor = vec4(col, 1.0);
}
