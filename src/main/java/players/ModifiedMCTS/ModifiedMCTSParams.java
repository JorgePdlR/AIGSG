package players.ModifiedMCTS;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import players.PlayerParameters;

import java.util.Arrays;


public class ModifiedMCTSParams extends PlayerParameters {

    public double K = Math.sqrt(2);
    public int rolloutLength = 10; // assuming we have a good heuristic
    public int maxTreeDepth = 100; // effectively no limit
    public double epsilon = 1e-6;
    public IStateHeuristic heuristic = AbstractGameState::getHeuristicScore;
    public int reflexiveIterations = 3;
    public int metamctsCalls = 4;

    public ModifiedMCTSParams() {
        this(System.currentTimeMillis());
    }

    public ModifiedMCTSParams(long seed) {
        super(seed);
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(0, 3, 10, 30, 100));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(1, 3, 10, 30, 100));
        addTunableParameter("epsilon", 1e-6);
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
        addTunableParameter("reflexiveIterations", 3, Arrays.asList(1, 3, 10, 30, 100));
        addTunableParameter("metamctsCalls", 4, Arrays.asList(1, 3, 10, 30, 100));
    }

    @Override
    public void _reset() {
        super._reset();
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
        reflexiveIterations = (int) getParameterValue("reflexiveIterations");
        metamctsCalls = (int) getParameterValue("metamctsCalls");
    }

    @Override
    protected ModifiedMCTSParams _copy() {
        // All the copying is done in TunableParameters.copy()
        // Note that any *local* changes of parameters will not be copied
        // unless they have been 'registered' with setParameterValue("name", value)
        return new ModifiedMCTSParams(System.currentTimeMillis());
    }

    public IStateHeuristic getHeuristic() {
        return heuristic;
    }

    @Override
    public ModifiedMCTSPlayer instantiate() {
        return new ModifiedMCTSPlayer((ModifiedMCTSParams) this.copy());
    }

}
