import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public class OutlineGenerator {
  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args[0]);
    Path sourceRoot = root.resolve("output/image-grid-split/source");
    Path output = root.resolve("output/image-grid-split/structure-outlines");
    Path layers = root.resolve("assets/sprites/units/upgrade/layers");
    Path parts = root.resolve("assets/sprites/units/upgrade/parts");
    Files.createDirectories(output);

    generate(sourceRoot.resolve("lutian.png"), output.resolve("king-lutian-outline.png"), 4);
    for (int i = 1; i <= 12; i++) {
      String name = String.format("lutian-layer-%02d", i);
      generate(layers.resolve(name + ".png"), output.resolve(name + "-outline.png"), 4);
      cropPartOutline(
          output.resolve(name + "-outline.png"),
          parts.resolve(String.format("lutian-part-%02d-outline.png", i)),
          PART_BOUNDS[i - 1]);
    }
  }

  private static final int[][] PART_BOUNDS = {
      {163, 42, 274, 288},
      {158, 242, 386, 466},
      {313, 147, 486, 390},
      {414, 241, 642, 466},
      {525, 42, 636, 287},
      {55, 392, 275, 639},
      {276, 391, 525, 623},
      {526, 393, 744, 639},
      {180, 528, 400, 836},
      {401, 528, 620, 836},
      {59, 639, 360, 859},
      {440, 639, 740, 859}
  };

  private static void cropPartOutline(
      Path sourcePath, Path outputPath, int[] bounds) throws Exception {
    BufferedImage source = ImageIO.read(sourcePath.toFile());
    int x = bounds[0];
    int y = bounds[1];
    int width = bounds[2] - bounds[0] + 1;
    int height = bounds[3] - bounds[1] + 1;
    BufferedImage cropped = source.getSubimage(x, y, width, height);
    ImageIO.write(cropped, "png", outputPath.toFile());
  }

  private static void generate(Path sourcePath, Path outputPath, int radius) throws Exception {
    BufferedImage source = ImageIO.read(sourcePath.toFile());
    BufferedImage outline = new BufferedImage(
        source.getWidth(),
        source.getHeight(),
        BufferedImage.TYPE_INT_ARGB);

    int width = source.getWidth();
    int height = source.getHeight();
    int gold = 0xffffd36b;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int alpha = source.getRGB(x, y) >>> 24;
        if (alpha <= 8) {
          continue;
        }

        boolean edge = false;
        for (int oy = -radius; oy <= radius && !edge; oy++) {
          int ny = y + oy;
          for (int ox = -radius; ox <= radius; ox++) {
            int nx = x + ox;
            if (nx < 0 || ny < 0 || nx >= width || ny >= height
                || (source.getRGB(nx, ny) >>> 24) <= 8) {
              edge = true;
              break;
            }
          }
        }

        if (edge) {
          outline.setRGB(x, y, gold);
        }
      }
    }

    ImageIO.write(outline, "png", outputPath.toFile());
  }
}
