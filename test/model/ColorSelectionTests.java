package model;
import java.util.*;

import model.BoardRepository;
import model.Difficulty;
import model.GameEngine;
import model.GameSession;
import model.PlayerColor;
/** Regression tests for selectable unique player colors. */
public final class ColorSelectionTests {
    public static void main(String[] args){
        GameEngine engine=new GameEngine(new BoardRepository());
        GameSession custom=engine.start(Difficulty.MEDIUM,List.of("A","B","C"),1,
                 List.of(PlayerColor.BLUE,PlayerColor.RED,PlayerColor.YELLOW));
        check(custom.current().name().equals("B")&&custom.current().color()==PlayerColor.RED,"first-player rotation keeps selected color");
        check(custom.players().get(1).color()==PlayerColor.YELLOW,"next player keeps their color");
        check(custom.players().get(2).color()==PlayerColor.BLUE,"wraparound player keeps their color");
        try{engine.start(Difficulty.EASY,List.of("A","B"),0,List.of(PlayerColor.RED,PlayerColor.RED));
            throw new AssertionError("duplicate colors accepted");}
        catch(IllegalArgumentException expected){System.out.println(" PASS duplicate colors rejected");}
        System.out.println("4 color selection tests PASSED, 0 FAILED");
    }
    private static void check(boolean ok,String description){
        if(!ok)throw new AssertionError(description);System.out.println(" PASS "+description);
    }
}
