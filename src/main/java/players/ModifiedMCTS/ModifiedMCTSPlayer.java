package players.ModifiedMCTS;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.List;
import java.util.Random;


/**
 * This is a simple version of MCTS that may be useful for newcomers to TAG and MCTS-like algorithms
 * It strips out some of the additional configuration of MCTSPlayer. It uses BasicTreeNode in place of
 * SingleTreeNode.
 */
public class ModifiedMCTSPlayer extends AbstractPlayer {

    Random rnd;
    ModifiedMCTSParams params;

    public ModifiedMCTSPlayer() {
        this(System.currentTimeMillis());
    }

    public ModifiedMCTSPlayer(long seed) {
        this.params = new ModifiedMCTSParams(seed);
        rnd = new Random(seed);
        setName("Basic MCTS");

        // These parameters can be changed, and will impact the Basic MCTS algorithm
        this.params.K = Math.sqrt(2);
        this.params.rolloutLength = 10;
        this.params.maxTreeDepth = 5;
        this.params.epsilon = 1e-6;
        this.params.reflexiveIterations = 3;
        this.params.metamctsCalls = 4;

    }

    public ModifiedMCTSPlayer(ModifiedMCTSParams params) {
        this.params = params;
        rnd = new Random(params.getRandomSeed());
        setName("Modified MCTS");
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> actions) {
        // Search for best action from the root
        ModifiedTreeNode root = new ModifiedTreeNode(this, null, gameState, rnd);

        // metamctsSearch does all of the hard work
        root.metamctsSearch();

        // Return best action
        return root.bestAction();
    }


    public void setStateHeuristic(IStateHeuristic heuristic) {
        this.params.heuristic = heuristic;
    }


    @Override
    public String toString() {
        return "Modified MCTS";
    }

    @Override
    public ModifiedMCTSPlayer copy() {
        return this;
    }
}