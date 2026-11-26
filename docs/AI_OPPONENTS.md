# AI opponents

Game Setup lets each player slot use Human Player, Easy AI, Medium AI, or Hard AI. Board difficulty controls game rules independently of these choices.

- Easy chooses uniformly from legal moves.
- Medium scores legal moves for captures, progress, castle exits, safe destinations, and nearby opposing pieces.
- Hard additionally estimates capture risk over the existing board's regular and special die outcomes, values two-piece blocks, and favors positions that make an exact finish more likely. These estimates guide choices. The game engine resolves every move.

On question squares, Easy guesses uniformly. Medium uses a word-overlap heuristic on the displayed prompt and choices with probabilities of 65%, 45%, and 25% for easy, medium, and hard questions. Hard uses 90%, 75%, and 55%. Otherwise the computer guesses uniformly. Ties in the heuristic are resolved randomly, and the heuristic can choose an incorrect answer. The normal question dialog checks the selected choice against the answer key and shows the result.

Computer decisions use randomness separate from game dice and surprises. AI actions use short Swing timers, suspend while the board is paused, and use the same turn and animation paths as human actions.
