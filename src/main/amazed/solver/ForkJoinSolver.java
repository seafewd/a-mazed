package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // ID of player
    private final int player;

    // thread safe ArrayList
    private final CopyOnWriteArrayList<ForkJoinSolver> threads = new CopyOnWriteArrayList<>();

    // atomic boolean as global flag for goal found - if this is true, stop working!
    private static final AtomicBoolean GOAL_FOUND = new AtomicBoolean(false);


    /**
     * initialize with empty thread safe data structures
     */
    @Override
    protected void initStructures() {

        super.initStructures();



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
        this.player = maze.newPlayer(start);
        this.visited = new ConcurrentSkipListSet<>();
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
        this.start = start;
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
     *
     * @param visited     set of already visited node IDs
     */
    public ForkJoinSolver(Maze maze, int player, int start, int forkAfter, Set<Integer> visited) {
        super(maze);
        this.start = start;
        this.forkAfter = forkAfter;
        this.visited = visited;
        this.player = player;
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
        return parallelSearch(start);
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
        if (!visited.add(current))
            return null;


        // if current node is goal, set global flag and return full path
        if (maze.hasGoal(current)) {
            GOAL_FOUND.set(true);
            waitForSolvers();
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
                visited.add(neighbor);
            }
        }

        // if work is small enough, 'just do it'
        // else fork with current data
        if (unvisited.size() == 1) {
            Integer nextNode = unvisited.iterator().next();
            maze.move(player, nextNode);
            return parallelSearch(nextNode);
        } else {
            for (Integer nextNode : unvisited) {
                predecessor.put(nextNode, current);
                int newPlayer = maze.newPlayer(nextNode);
                ForkJoinSolver newSolver = new ForkJoinSolver(maze, forkAfter, newPlayer, nextNode, visited);
                threads.add(newSolver);
                newSolver.fork();
            }
        }
        // go through all lists of neighbors in threads doing work
        // join with respective partial result
        List<Integer> pathToGoal = waitForSolvers();
        if (pathToGoal != null) {
            Integer threadStartNode = pathToGoal.get(0);
            List<Integer> pathFromStart = pathFromTo(start, threadStartNode);
            pathFromStart.addAll(pathToGoal);
            System.out.println("path??" + pathFromStart);
            return pathFromStart;
        }
        return null;
    }

    private List<Integer> waitForSolvers() {
        List<Integer> path = null;

        for (ForkJoinSolver thread : threads) {
            List<Integer> partialResult = thread.join();
            if (partialResult != null)
                path = partialResult;
        }
        return path;
    }

}
