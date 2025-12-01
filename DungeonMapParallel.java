/**
 * DungeonMapParallel.java
 *
 * Represents the dungeon terrain for the Dungeon Hunter assignment.
 * Methods to compute power (mana) values in the dungeon grid,
 * to find the neighbouring cell with highest mana value and
 * to visualise the power of visited cells.
 * Parallel version with thread-safe operations.
 *
 * C Olebuike 2025
 */

import java.util.Random;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

public class DungeonMapParallel {

    public static final int PRECISION = 10000;
    public static final int RESOLUTION = 5;

    private int rows, columns; // dungeonGrid size
    private double xmin, xmax, ymin, ymax; // x and y dungeon limits
    private int [][] manaMap;
    private int [][] visit;
    private int dungeonGridPointsEvaluated;
    private double bossX;
    private double bossY;
    private double decayFactor;

    // constructor
    public DungeonMapParallel(double xmin, double xmax,
                              double ymin, double ymax,
                              int seed) {
        super();
        this.xmin = xmin;
        this.xmax = xmax;
        this.ymin = ymin;
        this.ymax = ymax;

        this.rows = (int) Math.round((xmax - xmin) * RESOLUTION); // grid resolution fixed
        this.columns = (int) Math.round((ymax - ymin) * RESOLUTION);

        // Randomly place the boss peak
        Random rand = (seed == 0) ? new Random() : new Random(seed);
        double xRange = xmax - xmin;
        this.bossX = xmin + (xRange) * rand.nextDouble();
        this.bossY = ymin + (ymax - ymin) * rand.nextDouble();
        // Calculate decay factor based on range
        this.decayFactor = 2.0 / (xRange * 0.1);

        manaMap = new int[rows][columns];
        visit = new int[rows][columns];
        dungeonGridPointsEvaluated = 0;

        /* Terrain initialization */
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                manaMap[i][j] = Integer.MIN_VALUE; // means mana not yet measured
                visit[i][j] = -1; // grid point not yet visited
            }
        }
    }

    // has this site been visited before?
    boolean visited(int x, int y) {
        return visit[x][y] != -1;
    }

    void setVisited(int x, int y, int id) {
        if (visit[x][y] == -1) { // don't reset
            visit[x][y] = id;
        }
    }

    /**
     * Evaluates mana at a dungeonGrid coordinate (x, y) in the dungeon,
     * and writes it to the map.
     */
    int getManaLevel(int x, int y) {
        if (visited(x,y)) return manaMap[x][y];
        if (manaMap[x][y] > Integer.MIN_VALUE) return manaMap[x][y];

        /* Calculate the coordinates of the point in the ranges */
        double x_coord = xmin + ((xmax - xmin) / rows) * x;
        double y_coord = ymin + ((ymax - ymin) / columns) * y;
        double dx = x_coord - bossX;
        double dy = y_coord - bossY;
        double distanceSquared = dx * dx + dy * dy;

        /* The function to compute the mana value */
        double mana = (2 * Math.sin(x_coord + 0.1 * Math.sin(y_coord / 5.0) + Math.PI / 2) *
                       Math.cos((y_coord + 0.1 * Math.cos(x_coord / 5.0) + Math.PI / 2) / 2.0) +
                       0.7 * Math.sin((x_coord * 0.5) + (y_coord * 0.3) +
                                      0.2 * Math.sin(x_coord / 6.0) + Math.PI / 2) +
                       0.3 * Math.sin((x_coord * 1.5) - (y_coord * 0.8) +
                                      0.15 * Math.cos(y_coord / 4.0)) +
                       -0.2 * Math.log(Math.abs(y_coord - Math.PI * 2) + 0.1) +
                       0.5 * Math.sin((x_coord * y_coord) / 4.0 + 0.05 * Math.sin(x_coord)) +
                       1.5 * Math.cos((x_coord + y_coord) / 5.0 + 0.1 * Math.sin(y_coord)) +
                       3.0 * Math.exp(-0.03 * ((x_coord - bossX - 15) * (x_coord - bossX - 15) +
                                               (y_coord - bossY + 10) * (y_coord - bossY + 10))) +
                       8.0 * Math.exp(-0.01 * distanceSquared) +
                       2.0 / (1.0 + 0.05 * distanceSquared));

        /* Transform to fixed point precision */
        int fixedPoint = (int)(PRECISION * mana);
        manaMap[x][y] = fixedPoint;
        dungeonGridPointsEvaluated++; // simple counter
        return fixedPoint;
    }

    /**
     * Function to return the neighbouring cell direction with highest mana
     */
    HuntParallel.Direction getNextStepDirection(int x, int y) {
        HuntParallel.Direction climbDirection = HuntParallel.Direction.STAY;
        int localMax = getManaLevel(x, y);

        int[][] directions = {
            {-1,  0}, { 1,  0}, { 0, -1}, { 0,  1},
            {-1, -1}, { 1, -1}, {-1,  1}, { 1,  1}
        };

        HuntParallel.Direction[] directionEnums = {
            HuntParallel.Direction.LEFT,
            HuntParallel.Direction.RIGHT,
            HuntParallel.Direction.UP,
            HuntParallel.Direction.DOWN,
            HuntParallel.Direction.UP_LEFT,
            HuntParallel.Direction.UP_RIGHT,
            HuntParallel.Direction.DOWN_LEFT,
            HuntParallel.Direction.DOWN_RIGHT
        };

        for (int i = 0; i < directions.length; i++) {
            int newX = x + directions[i][0];
            int newY = y + directions[i][1];

            if (newX >= 0 && newX < rows && newY >= 0 && newY < columns) {
                int power = getManaLevel(newX, newY);
                if (power > localMax) {
                    localMax = power;
                    climbDirection = directionEnums[i];
                }
            }
        }

        return climbDirection;
    }

    /**
     * Generates an image from the dungeon grid.
     * Unvisited cells are colored black, while visited cells follow a black -> purple -> red -> white gradient.
     */
    public void visualisePowerMap(String filename, boolean path) {
        int width = manaMap.length;
        int height = manaMap[0].length;

        // safeguard: do not generate huge images
        if (width * height > 2_000_000) {
            System.out.println("Map too large, skipping visualisation: " + filename);
            return;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int value = manaMap[x][y];
                if (value == Integer.MIN_VALUE) continue;
                if (value < min) min = value;
                if (value > max) max = value;
            }
        }
        double range = (max > min) ? (max - min) : 1.0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color color;
                if (path && !visited(x, y)) color = Color.BLACK;
                else if (manaMap[x][y] == Integer.MIN_VALUE) color = Color.BLACK;
                else {
                    double normalized = (manaMap[x][y] - min) / range; // 0-1
                    color = mapHeightToColor(normalized);
                }
                image.setRGB(x, height - 1 - y, color.getRGB());
            }
        }
        try {
            File output = new File(filename);
            ImageIO.write(image, "png", output);
            System.out.println("map saved to " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Maps normalized height [0..1] to black -> purple -> red -> white.
     */
    private Color mapHeightToColor(double normalized) {
        normalized = Math.max(0, Math.min(1, normalized));

        int r = 0, g = 0, b = 0;

        if (normalized < 0.33) {
            double t = normalized / 0.33;
            r = (int) (128 * t);
            g = 0;
            b = (int) (128 + 127 * t);
        } else if (normalized < 0.66) {
            double t = (normalized - 0.33) / 0.33;
            r = (int) (128 + 127 * t);
            g = 0;
            b = (int) (255 - 255 * t);
        } else {
            double t = (normalized - 0.66) / 0.34;
            r = 255;
            g = (int) (255 * t);
            b = (int) (255 * t);
        }

        return new Color(r, g, b);
    }

    public int getGridPointsEvaluated() {
        return dungeonGridPointsEvaluated;
    }

    public double getXcoord(int x) {
        return xmin + ((xmax - xmin) / rows) * x;
    }
    public double getYcoord(int y) {
        return ymin + ((ymax - ymin) / columns) * y;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }
}
