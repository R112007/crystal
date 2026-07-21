attribute vec4 a_position;

uniform mat4 u_proj;
uniform mat4 u_trans;

varying vec3 v_pos;

void main(){
    vec4 worldPos = u_trans * a_position;
    gl_Position = u_proj * worldPos;
    v_pos = worldPos.xyz;
}
