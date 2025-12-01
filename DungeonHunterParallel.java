/*
 * Main driver for the parallel Dungeon Hunter assignment.
 * This program initializes the dungeon map and performs a series of parallel searches
 * to locate the global maximum using the Fork-Join framework.
 *
 * C Olebuike 25
 */

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Parallel version of DungeonHunter using Fork/Join framework
 */
public class DungeonHunterParallel {
    static final boolean DEBUG = false;

    // Sequential cutoff for divide-and-conquer
    private static final int THRESHOLD = 100;

    // Timers
    static long startTime = 0;
    static long endTime = 0;
    private static void tick() { startTime = System.currentTimeMillis(); }
    private static void tock() { endTime = System.currentTimeMillis(); }

    /**
     * RecursiveTask for parallel search execution
     */
    static class ParallelSearch extends RecursiveTask<SearchResult> {
        private final HuntParallel[] searches;
        private final int start;
        private final int end;

        public ParallelSearch(HuntParallel[] searches, int start, int end) {
            this.searches = searches;
            this.start = start;
            this.end = end;
        }

        @Override
        protected SearchResult compute() {
            if (end - start <= THRESHOLD) {
                // Sequential execution for small ranges
                return executeSearchesSequentially();
            } else {
                // Divide and conquer
                int mid = (start + end) / 2;
                ParallelSearch left = new ParallelSearch(searches, start, mid);
                ParallelSearch right = new ParallelSearch(searches, mid, end);

                left.fork();
                SearchResult rightResult = right.compute();
                SearchResult leftResult = left.join();

                // Combine results
                return combineResults(leftResult, rightResult);
            }
        }

        private SearchResult executeSearchesSequentially() {
            int maxMana = Integer.MIN_VALUE;
            int bestSearchIndex = -1;

            for (int i = start; i < end; i++) {
                int localMax = searches[i].findManaPeak();
                if (localMax > maxMana) {
                    maxMana = localMax;
                    bestSearchIndex = i;
                }
                if (DEBUG) {
                    System.out.println("Shadow " + searches[i].getID() +
                                     " finished at " + localMax +
                                     " in " + searches[i].getSteps());
                }
            }

            return new SearchResult(maxMana, bestSearchIndex);
        }

        private SearchResult combineResults(SearchResult left, SearchResult right) {
            if (left.maxMana >= right.maxMana) {
                return left;
            } else {
                return right;
            }
        }
    }

    /**
     * Helper class to store search results
     */
    static class SearchResult {
        final int maxMana;
        final int searchIndex;

        SearchResult(int maxMana, int searchIndex) {
            this.maxMana = maxMana;
            this.searchIndex = searchIndex;
        }
    }

    public static void main(String[] args) {
        double xmin, xmax, ymin, ymax;
        DungeonMapParallel dungeon;

        int numSearches = 10, gateSize = 10;
        HuntParallel[] searches;

        int randomSeed = 0;

        if (args.length != 3) {
            System.out.println("Incorrect number of command line arguments provided.");
            System.exit(0);
        }

        try {
            gateSize = Integer.parseInt(args[0]);
            if (gateSize <= 0) {
                throw new IllegalArgumentException("Grid size must be greater than 0.");
            }

            numSearches = (int) (Double.parseDouble(args[1]) * (gateSize * 2) *
                                (gateSize * 2) * DungeonMapParallel.RESOLUTION);

            randomSeed = Integer.parseInt(args[2]);
            if (randomSeed < 0) {
                throw new IllegalArgumentException("Random seed must be non-negative.");
            }

        } catch (NumberFormatException e) {
            System.err.println("Error: All arguments must be numeric.");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }

        xmin = -gateSize;
        xmax = gateSize;
        ymin = -gateSize;
        ymax = gateSize;
        dungeon = new DungeonMapParallel(xmin, xmax, ymin, ymax, randomSeed);

        int dungeonRows = dungeon.getRows();
        int dungeonColumns = dungeon.getColumns();
        searches = new HuntParallel[numSearches];

        // Initialize searches at random locations
        // Use same seed logic as original for reproducibility
        java.util.Random seededRand = (randomSeed > 0) ? new java.util.Random(randomSeed) : new java.util.Random();

        for (int i = 0; i < numSearches; i++) {
            searches[i] = new HuntParallel(i + 1,
                                         seededRand.nextInt(dungeonRows),
                                         seededRand.nextInt(dungeonColumns),
                                         dungeon);
        }

        // Execute parallel searches using ForkJoinPool
        tick();

        ForkJoinPool pool = new ForkJoinPool();
        ParallelSearch task = new ParallelSearch(searches, 0, numSearches);
        SearchResult result = pool.invoke(task);

        tock();

        // Output results (format aligned with serial version; timing line parsable by marker)
        System.out.printf("\t dungeon size: %d,\n", gateSize);
        System.out.printf("\t rows: %d, columns: %d\n", dungeonRows, dungeonColumns);
        System.out.printf("\t x: [%f, %f], y: [%f, %f]\n", xmin, xmax, ymin, ymax);
        System.out.printf("\t Number searches: %d\n", numSearches);

        // IMPORTANT: performance script expects exactly " time: %d ms" with one leading space and no tab
        System.out.printf(" time: %d ms\n", endTime - startTime);

        int tmp = dungeon.getGridPointsEvaluated();
        System.out.printf("\tnumber dungeon grid points evaluated: %d  (%2.0f%s)\n",
                         tmp, (tmp * 1.0 / (dungeonRows * dungeonColumns * 1.0)) * 100.0, "%");

        System.out.printf("Dungeon Master (mana %d) found at:  ", result.maxMana);
        System.out.printf("x=%.1f y=%.1f\n\n",
                         dungeon.getXcoord(searches[result.searchIndex].getPosRow()),
                         dungeon.getYcoord(searches[result.searchIndex].getPosCol()));

        dungeon.visualisePowerMap("visualiseSearch.png", false);
        dungeon.visualisePowerMap("visualiseSearchPath.png", true);

        pool.shutdown();
    }
}
