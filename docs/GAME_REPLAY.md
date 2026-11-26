# Game replay

Finish a match, then select it in Game History and choose **Watch Replay**. The timeline moves between recorded events. Use Play, Pause, Restart, Previous Event, and Next Event to navigate. Playback offers 0.5×, 1×, 2×, and 4× speeds. It advances by event so waiting for input in the original match does not carry over. Capture animation uses the selected speed. Pausing or seeking during an animation settles the board at the selected event.

The history CSV keeps its original schema. Completed recordings are versioned, compressed sidecar files in the history CSV's sibling `history.csv.replays` directory. Each archive uses the CSV game ID and is checked against the history row before playback. Keep the sidecar directory with the CSV when copying a local history. A CSV export contains the match results.

Legacy matches have no recording and show **Replay unavailable**. Missing, incompatible, or damaged archives also appear unavailable. Playback uses a separate board projection and recorded outcomes. It generates no game outcomes and makes no writes to history or the question bank.
