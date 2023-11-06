package players.ModifiedMCTS;

import core.AbstractGameState;
import core.actions.AbstractAction;
import org.sparkproject.dmg.pmml.False;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class ModifiedTreeNode {
    // Root node of tree
    ModifiedTreeNode root;
    // Parent of this node
    ModifiedTreeNode parent;
    // Children of this node
    Map<AbstractAction, ModifiedTreeNode> children = new HashMap<>();
    // Depth of this node
    final int depth;

    // Total value of this node
    private double totValue;
    // Number of visits
    private int nVisits;
    // Number of FM calls and State copies up until this node
    private int fmCallsCount;
    // Parameters guiding the search
    private ModifiedMCTSPlayer player;
    private Random rnd;
    private RandomPlayer randomPlayer = new RandomPlayer();

    // State in this node (closed loop)
    private AbstractGameState state;

    // Level of reflexive call, if metamctsCalls = 1 that means that MCTS will be called once at level 1 and once at
    // level 0. metamcts variable indicates in which level we are. If we are in level 0 no more calls should be
    // performed to MCTS
    private int metamcts;

    private int reflexiveNumberCalls;

    protected ModifiedTreeNode(ModifiedMCTSPlayer player, ModifiedTreeNode parent, AbstractGameState state, Random rnd) {
        this.player = player;
        this.fmCallsCount = 0;
        this.parent = parent;
        this.root = parent == null ? this : parent.root;
        totValue = 0.0;
        setState(state);
        if (parent != null) {
            depth = parent.depth + 1;
        } else {
            depth = 0;
        }
        this.rnd = rnd;
        randomPlayer.setForwardModel(player.getForwardModel());
    }
    /**
     * Performs full MCTS search, using the defined budget limits.
     */
    void metamctsSearch() {

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining = 0;
        int remainingLimit = player.params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(player.params.budget);
            remaining = elapsedTimer.remainingTimeMillis();
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            ModifiedTreeNode selected = treePolicy();

            // Set metamcts calls
            selected.metamcts = player.params.metamctsCalls;

            selected.reflexiveNumberCalls = player.params.reflexiveCalls;

            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut(remaining);
            // Back up the value of the rollout through the tree
            selected.backUp(delta);
            // Finished iteration
            numIters++;

            // Check stopping condition
            PlayerConstants budgetType = player.params.budgetType;
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                // $$$ REMOVE
                if (remaining < 0) {
                    System.out.printf("WARNING: acumTimeTaken %f avgTimeTaken %f remaining %d\n", acumTimeTaken, avgTimeTaken, remaining);
                }
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= player.params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = fmCallsCount > player.params.budget;
            }
        }
    }
    /**
     * Performs full MCTS search, using the defined budget limits
     * inside the current MCTS search. In other words we are doing
     * reflexive Monte Carlo by calling MCTS inside MCTS
     */
    void mctsSearch(long timeRemaining) {
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();

        if (player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(timeRemaining);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            ModifiedTreeNode selected = treePolicy();

            // Update metaMCTS value
            selected.metamcts = this.metamcts;

            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut(timeRemaining);
            // Back up the value of the rollout through the tree
            selected.backUp(delta);
            // Finished iteration
            numIters++;

            // Check stopping condition.
            if (player.params.budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                // $$$ REMOVE
                if (remaining < 0) {
                    System.out.printf("WARNING reflexive: acumTimeTaken %f avgTimeTaken %f remaining %d\n", acumTimeTaken, avgTimeTaken, remaining);
                }
                stop = numIters >= player.params.reflexiveIterations || remaining <= 2 * avgTimeTaken || remaining <= timeRemaining * .95;
            } else {
                // Since we are in reflexive MCTS stop after number of iterations is reached
                stop = numIters >= player.params.reflexiveIterations;
            }
        }
    }

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private ModifiedTreeNode treePolicy() {

        ModifiedTreeNode cur = this;

        // Keep iterating while the state reached is not terminal and the depth of the tree is not exceeded
        while (cur.state.isNotTerminal() && cur.depth < player.params.maxTreeDepth) {
            if (!cur.unexpandedActions().isEmpty()) {
                // We have an unexpanded action
                cur = cur.expand();
                return cur;
            } else {
                // Move to next child given by UCT function
                AbstractAction actionChosen = cur.ucb();
                cur = cur.children.get(actionChosen);
            }
        }

        return cur;
    }


    private void setState(AbstractGameState newState) {
        state = newState;
        if (newState.isNotTerminal())
            for (AbstractAction action : player.getForwardModel().computeAvailableActions(state, player.params.actionSpace)) {
                children.put(action, null); // mark a new node to be expanded
            }
    }

    /**
     * @return A list of the unexpanded Actions from this State
     */
    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).collect(toList());
    }

    /**
     * Expands the node by creating a new random child node and adding to the tree.
     *
     * @return - new child node.
     */
    private ModifiedTreeNode expand() {
        // Find random child not already created
        Random r = new Random(player.params.getRandomSeed());
        // pick a random unchosen action
        List<AbstractAction> notChosen = unexpandedActions();
        AbstractAction chosen = notChosen.get(r.nextInt(notChosen.size()));

        // copy the current state and advance it using the chosen action
        // we first copy the action so that the one stored in the node will not have any state changes
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        // then instantiate a new node
        ModifiedTreeNode tn = new ModifiedTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);
        return tn;
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++;
    }

    private AbstractAction ucb() {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;

        for (AbstractAction action : children.keySet()) {
            ModifiedTreeNode child = children.get(action);
            if (child == null)
                throw new AssertionError("Should not be here");
            else if (bestAction == null)
                bestAction = action;

            // Find child value
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + player.params.epsilon);

            // default to standard UCB
            double explorationTerm = player.params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + player.params.epsilon));
            // unless we are using a variant

            // Find 'UCB' value
            // If 'we' are taking a turn we use classic UCB
            // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uctValue = iAmMoving ? childValue : -childValue;
            uctValue += explorationTerm;

            // Apply small noise to break ties randomly
            uctValue = noise(uctValue, player.params.epsilon, player.rnd.nextDouble());

            // Assign value
            if (uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        if (bestAction == null)
            throw new AssertionError("We have a null value in UCT : shouldn't really happen!");

        root.fmCallsCount++;  // log one iteration complete
        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut(long remaining) {
        int rolloutDepth = 0; // counting from end of tree
        AbstractAction next;
        AbstractAction[] nextActions;

        int roundCounter = state.getRoundCounter();

        ////////////////////////////////////////////////////////////////
        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();

        if (player.params.rolloutLength > 0) {
            // $$$ Round counter should remain this way ?
            // $$$ Don't restrict exploring of meta monte carlo, lets apply the restriction for this turn
            // just to reflexive MCTS
            while (!finishRollout(rolloutState, rolloutDepth) && (!player.params.currentRound || roundCounter == rolloutState.getRoundCounter())) {
                // Use reflexive monte carlo just when is our turn if reflexiveInOpponent is false, otherwise use also reflexive
                // Monte Carlo in opponents turn.
                if (metamcts > 0 && reflexiveNumberCalls > 0 &&
                        (player.params.reflexiveInOpponent || (rolloutState.getTurnOwner() == this.player.getPlayerID()))){
                    ModifiedTreeNode reflexiveRoot = new ModifiedTreeNode(this.player, null, rolloutState, rnd);
                    reflexiveRoot.metamcts -= 1;
                    reflexiveRoot.mctsSearch(remaining);
                    next = reflexiveRoot.bestAction();
                    // $$$ Not used, may remove
                    nextActions = reflexiveRoot.listOfBestActions(2);
                    reflexiveNumberCalls--;
                }
                // Get random action when we are not in meta Monte Carlo, or if we are in meta Monte Carlo, it is the
                // opponents turn and reflexiveInOpponent is false
                else {
                    next = randomPlayer.getAction(rolloutState, randomPlayer.getForwardModel().computeAvailableActions(rolloutState, randomPlayer.parameters.actionSpace));
                }
                advance(rolloutState, next);
                rolloutDepth++;
            }
                ///////////////////////////////////////////////////////////////
        }
        // Evaluate final state and return normalised score
        double value = player.params.getHeuristic().evaluateState(rolloutState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Illegal heuristic value - should be a number");
        return value;
    }

    /**
     * Checks if rollout is finished. Rollouts end on maximum length, or if game ended.
     *
     * @param rollerState - current state
     * @param depth       - current depth
     * @return - true if rollout finished, false otherwise
     */
    private boolean finishRollout(AbstractGameState rollerState, int depth) {
        if (depth >= player.params.rolloutLength)
            return true;

        // End of game
        return !rollerState.isNotTerminal();
    }

    /**
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result - value of rollout to backup
     */
    private void backUp(double result) {
        ModifiedTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            n = n.parent;
        }
    }

    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        for (AbstractAction action : children.keySet()) {
            if (children.get(action) != null) {
                ModifiedTreeNode node = children.get(action);
                double childValue = node.nVisits;

                // Apply small noise to break ties randomly
                childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                // Save best value (highest visit count)
                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = action;
                }
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

    /**
     * Calculates the best actions from the root according to the most visited nodes
     *
     * @return - the array of best AbstractAction
     */
    AbstractAction[] listOfBestActions(int size) {
        double[] bestValues;
        AbstractAction[] bestActions;

        bestActions = new AbstractAction[size];
        bestValues  = new double[size];

        // Initialize best values array
        for (int i = 0 ; i < size ; i++){
            bestValues[i]  = -Double.MAX_VALUE;
            bestActions[i] = null;
        }

        for (AbstractAction action : children.keySet()) {
            if (children.get(action) != null) {
                ModifiedTreeNode node = children.get(action);
                double childValue = node.nVisits;

                // Apply small noise to break ties randomly
                childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                // Save best value (highest visit count)
                if (size == 1 && childValue > bestValues[0]) {
                    bestValues[0] = childValue;
                    bestActions[0] = action;
                }
                for (int i = 0 ; i < size - 1; i++){
                    if (childValue > bestValues[i]){
                        // Move to the right all elements
                        for(int j = i; j < size - 1; j++){
                            double temp       = 0;
                            AbstractAction tempAction;

                            // Update values
                            temp              = bestValues[j + 1];
                            bestValues[j + 1] = bestValues[i];
                            bestValues[i]     = temp;

                            // Update actions
                            tempAction         = bestActions[j + 1];
                            bestActions[j + 1] = bestActions[i];
                            bestActions[i]      = tempAction;

                        }

                        // Assign action in the corresponding place
                        bestValues[i] = childValue;
                        bestActions[i] = action;
                        break;
                    }
                }
            }
        }

        if (bestActions[0] == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestActions;
    }

}
