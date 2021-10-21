package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver extends SequentialSolver {

    // ID of current node
    private final int current;

    // ID of player
    private final int player;

    // thread safe ArrayList
    private final CopyOnWriteArrayList<ForkJoinTask<List<Integer>>> threads = new CopyOnWriteArrayList<>();

    private static final AtomicBoolean GOAL_FOUND = new AtomicBoolean(false);

    /**
     * initialize with empty thread safe data structures
     */
    @Override
    protected void initStructures() {
        // skip list set for visited nodes
        visited = new ConcurrentSkipListSet<>();

        // skip list map for predecessors
        predecessor = new ConcurrentSkipListMap<>();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        current = start;
        player = maze.newPlayer(current);
        initStructures();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        super(maze);
        player = maze.newPlayer(start);
        current = start;
        this.forkAfter = forkAfter;
        initStructures();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * current node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after which a parallel task is forked; if
     *                    <code>forkAfter <= 0</code> the solver never forks new tasks
     * @param current     current node ID
     * @param visited     set of already visited node IDs
     * @param predecessor mapped predecessor, <fromID, toID>
     */
    public ForkJoinSolver(Maze maze, int current, int forkAfter, Set<Integer> visited, Map<Integer, Integer> predecessor) {
        super(maze);
        player = maze.newPlayer(current);
        this.current = current;
        this.forkAfter = forkAfter;
        this.visited = visited;
        this.predecessor = predecessor;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreachable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch(current);
    }


    private List<Integer> waitForOtherSolvers() {
        List<Integer> result = null;

        for (ForkJoinTask<List<Integer>> thread : threads) {
            List<Integer> partialPath = thread.join();
            if (partialPath != null) result = partialPath;
        }
        return result;
    }

    /**
     *
     * @param current current ID of node
     * @return
     *      full path if found
     *      null if not found
     */
    private List<Integer> parallelSearch(int current) {
        if (GOAL_FOUND.get())
            return null;

        // add current node ID to visited list
        visited.add(current);

        // if current node is goal, return full path
        if (maze.hasGoal(current)) {
            GOAL_FOUND.set(true);
            return pathFromTo(start, current);
        }

        // get all neighbors to set
        Set<Integer> neighbors = maze.neighbors(current);

        // create empty list of unvisited nodes
        List<Integer> unvisited = new ArrayList<>();

        // loop through all neighbors (neighbor)
        // if visited list does not contain current neighbor,
        // add it to unvisited and set its predecessor to current
        for (Integer neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                unvisited.add(neighbor);
                predecessor.put(neighbor, current);
            }
        }

        // if number of unvisited nodes is less than forkAfter value, then 'just do it'
        // else fork with current data
        if (unvisited.size() == 1) {
            Integer nextNode = unvisited.iterator().next();
            maze.move(player, nextNode);
            return parallelSearch(nextNode);
        } else
            for (Integer nextNode : unvisited) {
                threads.add(new ForkJoinSolver(maze, nextNode, forkAfter, visited, predecessor).fork());
                // debug
                System.out.println("im a new thread");
            }

        List<Integer> pathToGoal = waitForOtherSolvers();

        if (pathToGoal != null) {
            int mid = pathToGoal.remove(0);
            List<Integer> pathFromStart = pathFromTo(start, mid);
            pathFromStart.addAll(pathToGoal);
            return pathFromStart;
        }
        return null;

        // go through all lists of neighbors in threads doing work
        // join with respective partial result
        // List<Integer> path = null;
        // for (ForkJoinTask<List<Integer>> thread : threads) {
        //     List<Integer> partialResult = thread.join();
        //     if (partialResult != null) {
        //         path = partialResult;
        //     }
        // }
        // if (path == null) // debug
        //     System.out.println("path is null!");
        // // return path result
        // return path;
    }
}
