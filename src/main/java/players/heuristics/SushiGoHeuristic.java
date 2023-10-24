package players.heuristics;

import core.AbstractGameState;
import core.CoreConstants;
import core.components.Deck;
import core.interfaces.IStateHeuristic;

import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class SushiGoHeuristic implements IStateHeuristic {

    /**
     * This cares mostly about the raw game score - but will treat winning as a 50% bonus
     * and losing as halving it
     *
     * @param gs       - game state to evaluate and score.
     * @param playerId - player id
     * @return
     */
    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {

        // This is where I set the values for each card
        List<String> cards =                        Arrays.asList("Tempura","Sashimi","Dumpling","Maki","Maki-2","Maki-3","SalmonNigiri","SquidNigiri","EggNigiri","Pudding","Wasabi","Chopsticks");
        ArrayList<Float> points = new ArrayList<>(  Arrays.asList(  2.5f,     3.0f,   3.0f,      1.0f,     2.0f,   3.0f,       6.0f,       8.0f,          4.0f,       2.0f,      10.0f,    4.0f));

        // Now get the played cards from the
        AbstractGameState game  = (SGGameState) gs;
        Deck<SGCard> PlayerHand = ((SGGameState) gs).getPlayerHands().get(playerId);
        Deck<SGCard> playedCards =  ((SGGameState) gs).getPlayedCards().get(playerId);

        //List<String> PlayerHand = new Arrays.asList();
        //System.out.println("I am player: "+playerId+" ,My cards are :"+PlayerHand.get(playerId));
        System.out.println("The cards Played so far is :"+playedCards);


        double score = 0.0;

        for (int i = 0; i < playedCards.getSize(); i++) {
            int index = cards.indexOf(""+playedCards.get(i));
            //System.out.println(playedCards.get(i));
            score = score + points.get(index);
        }

        // I have added this value to merge the current score with the card value
        // The Heuristic is a combo of the current score + my values
        score = score + gs.getGameScore(playerId);


        // There was already a get getHeuristicScore() method in gameState
        //Both give same results so no need to change

        //double score = gs.getGameScore(playerId);
        //double score = gs.getHeuristicScore(playerId);



        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.WIN_GAME)
            return score * 1.5;

        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.LOSE_GAME)
            return score * 0.5;

        return score;
    }
}
