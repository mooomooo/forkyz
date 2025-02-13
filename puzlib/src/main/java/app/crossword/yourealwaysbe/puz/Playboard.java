package app.crossword.yourealwaysbe.puz;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import app.crossword.yourealwaysbe.util.WeakSet;

public class Playboard implements Serializable {
    private static final Logger LOG = Logger.getLogger(Playboard.class.getCanonicalName());

    private Map<Integer, Position> acrossWordStarts = new HashMap<Integer, Position>();
    private Map<Integer, Position> downWordStarts = new HashMap<Integer, Position>();
    private MovementStrategy movementStrategy = MovementStrategy.MOVE_NEXT_ON_AXIS;
    private Position highlightLetter = new Position(0, 0);
    private Puzzle puzzle;
    private String responder;
    private Box[][] boxes;
    private boolean across = true;
    private boolean showErrorsGrid;
    private boolean showErrorsCursor;
    private boolean skipCompletedLetters;
    private boolean preserveCorrectLettersInShowErrors;
    private boolean dontDeleteCrossing;
    private Set<PlayboardListener> listeners = WeakSet.buildSet();
    private int notificationDisabledDepth = 0;
    private Word previousWord = null;

    public Playboard(Puzzle puzzle,
                     MovementStrategy movementStrategy,
                     boolean preserveCorrectLettersInShowErrors,
                     boolean dontDeleteCrossing){
        this(puzzle, movementStrategy);
        this.preserveCorrectLettersInShowErrors = preserveCorrectLettersInShowErrors;
        this.dontDeleteCrossing = dontDeleteCrossing;
    }

    public Playboard(Puzzle puzzle, MovementStrategy movementStrategy) {
        this(puzzle);
        this.movementStrategy = movementStrategy;
    }

    public Playboard(Puzzle puzzle) {
        this.puzzle = puzzle;
        this.highlightLetter = this.puzzle.getPosition();
        if (this.highlightLetter == null)
            this.highlightLetter = new Position(0, 0);
        this.across = this.puzzle.getAcross();
        this.boxes = new Box[puzzle.getBoxes()[0].length][puzzle.getBoxes().length];

        for (int x = 0; x < puzzle.getBoxes().length; x++) {
            for (int y = 0; y < puzzle.getBoxes()[x].length; y++) {
                boxes[y][x] = puzzle.getBoxes()[x][y];

                if ((boxes[y][x] != null) && boxes[y][x].isAcross()) {
                    acrossWordStarts.put(boxes[y][x].getClueNumber(), new Position(y, x));
                }

                if ((boxes[y][x] != null) && boxes[y][x].isDown()) {
                    downWordStarts.put(boxes[y][x].getClueNumber(), new Position(y, x));
                }
            }
        }

        if (getCurrentBox() == null)
            this.moveRight(false);

        updateHistory();
    }

    public void setPreserveCorrectLettersInShowErrors(boolean value){
        this.preserveCorrectLettersInShowErrors = value;
    }

    /**
     * Whether to delete characters from crossing words
     */
    public void setDontDeleteCrossing(boolean value){
        this.dontDeleteCrossing = value;
    }

    public void setAcross(boolean across) {
        boolean changed = (this.across != across);
        this.across = across;
        if (this.puzzle != null) {
            this.puzzle.setAcross(across);
        }
        if (changed) {
            notifyChange();
        }
    }

    public boolean isAcross() {
        return across;
    }

    public Box[][] getBoxes() {
        return this.boxes;
    }

    /**
     * Returns null if no clue for current position
     */
    public Clue getClue() {
        int number = getClueNumber();
        return (number > -1)
            ? this.puzzle.getClues(this.isAcross()).getClue(number)
            : null;
    }

    /**
     * Clue number for current position or -1 if none
     */
    public int getClueNumber() {
        Position start = this.getCurrentWordStart();
        Box[][] boxes =  this.getBoxes();
        if (start.across >= 0 && start.across < boxes.length) {
            Box[] row = boxes[start.across];
            if (start.down >= 0 && start.down < row.length) {
                return row[start.down].getClueNumber();
            }
        }
        return -1;
    }

    /**
     * Returns -1 if no current clue
     */
    public int getCurrentClueIndex() {
        Clue clue = getClue();
        if (clue != null) {
            return puzzle.getClues(clue.getIsAcross())
                .getClueIndex(clue.getNumber());
        } else {
            return -1;
        }
    }

    public Note getNote() {
        Clue c = this.getClue();
        if (c != null)
            return this.puzzle.getNote(c.getNumber(), this.across);
        else
            return null;
    }

    public Box getCurrentBox() {
        return getCurrentBoxOffset(0, 0);
    }

    /**
     * Get the box to the left in the direction of the clue
     *
     * I.e. if across, get box one north, if down, get box one east
     */
    public Box getAdjacentBoxLeft() {
        return getCurrentBoxOffset(across ? 0 : 1,
                                   across ? -1 : 0);
    }

    /**
     * Get the box to the right in the direction of the clue
     *
     * I.e. if across, get box one south, if down, get box one west
     */
    public Box getAdjacentBoxRight() {
        return getCurrentBoxOffset(across ? 0 : -1,
                                   across ? 1 : 0);
    }

    /**
     * Get the box at the given offset from current box
     *
     * Null if no box
     */
    private Box getCurrentBoxOffset(int offsetAcross, int offsetDown) {
        Position currentPos = getHighlightLetter();
        Box[][] boxes = getBoxes();

        int offAcross = currentPos.across + offsetAcross;
        int offDown = currentPos.down + offsetDown;

        if (0 <= offAcross && offAcross < boxes.length &&
            0 <= offDown && offDown < boxes[offAcross].length)
            return boxes[offAcross][offDown];
        else
            return null;
    }

    public Word getCurrentWord() {
        Word w = new Word();
        w.start = this.getCurrentWordStart();
        w.across = this.isAcross();
        w.length = this.getWordRange();

        return w;
    }

    public Box[] getCurrentWordBoxes() {
        Word currentWord = this.getCurrentWord();
        Box[] result = new Box[currentWord.length];

        int across = currentWord.start.across;
        int down = currentWord.start.down;

        for (int i = 0; i < result.length; i++) {
            int newAcross = across;
            int newDown = down;

            if (currentWord.across) {
                newAcross += i;
            } else {
                newDown += i;
            }

            result[i] = this.boxes[newAcross][newDown];
        }

        return result;
    }

    public String getCurrentWordResponse() {
        Box[] boxes = getCurrentWordBoxes();
        char[] letters = new char[boxes.length];

        for (int i = 0; i < boxes.length; i++) {
            letters[i] = boxes[i].getResponse();
        }
        return new String(letters);
    }

    public Position[] getCurrentWordPositions() {
        Word currentWord = this.getCurrentWord();
        Position[] result = new Position[currentWord.length];
        int across = currentWord.start.across;
        int down = currentWord.start.down;

        for (int i = 0; i < result.length; i++) {
            int newAcross = across;
            int newDown = down;

            if (currentWord.across) {
                newAcross += i;
            } else {
                newDown += i;
            }

            result[i] = new Position(newAcross, newDown);
        }

        return result;
    }

    public Position getCurrentWordStart() {
        if (this.isAcross()) {
            int col = this.highlightLetter.across;
            Box b = null;

            while (b == null) {
                try {
                    if ((boxes[col][this.highlightLetter.down] != null) &&
                            boxes[col][this.highlightLetter.down].isAcross()) {
                        b = boxes[col][this.highlightLetter.down];
                    } else {
                        col--;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            return new Position(col, this.highlightLetter.down);
        } else {
            int row = this.highlightLetter.down;
            Box b = null;

            while (b == null) {
                try {
                    if ((boxes[this.highlightLetter.across][row] != null) &&
                            boxes[this.highlightLetter.across][row].isDown()) {
                        b = boxes[this.highlightLetter.across][row];
                    } else {
                        row--;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            return new Position(this.highlightLetter.across, row);
        }
    }

    public void setCurrentWord(String response) {
        Box[] boxes = getCurrentWordBoxes();
        int length = Math.min(boxes.length, response.length());
        for (int i = 0; i < length; i++) {
            boxes[i].setResponse(response.charAt(i));
        }
        notifyChange();
    }

    public void setCurrentWord(Box[] response) {
        Box[] boxes = getCurrentWordBoxes();
        int length = Math.min(boxes.length, response.length);
        for (int i = 0; i < length; i++) {
            boxes[i].setResponse(response[i].getResponse());
        }
        notifyChange();
    }

    public Word setHighlightLetter(Position highlightLetter) {
        Word w = this.getCurrentWord();
        int x = highlightLetter.across;
        int y = highlightLetter.down;

        pushNotificationDisabled();

        if (highlightLetter.equals(this.highlightLetter)) {
            toggleDirection();
        } else {
            if ((boxes.length > x) && (x >= 0) &&
                (boxes[x].length > y) && (y >= 0) &&
                (boxes[x][y] != null)) {
                this.highlightLetter = highlightLetter;

                if (this.puzzle != null) {
                    this.puzzle.setPosition(highlightLetter);
                }

                if ((isAcross() && !boxes[x][y].isPartOfAcross()) ||
                    (!isAcross() && !boxes[x][y].isPartOfDown())) {
                    toggleDirection();
                }
            }
        }

        popNotificationDisabled();

        notifyChange();

        return w;
    }

    /**
     * Returns true if the position is part of a word (not blank cell)
     */
    public boolean isInWord(Position p) {
        int x = p.across;
        int y = p.down;
        return ((boxes.length > x) && (x >= 0) &&
                (boxes[x].length > y) && (y >= 0) &&
                (boxes[x][y] != null));
    }

    public Position getHighlightLetter() {
        return highlightLetter;
    }

    public void setMovementStrategy(MovementStrategy movementStrategy) {
        this.movementStrategy = movementStrategy;
    }

    public Puzzle getPuzzle() {
        return this.puzzle;
    }

    /**
     * @param responder the responder to set
     */
    public void setResponder(String responder) {
        this.responder = responder;
    }

    /**
     * @return the responder
     */
    public String getResponder() {
        return responder;
    }

    /**
     * Show errors across the whole grid
     */
    public void setShowErrorsGrid(boolean showErrors) {
        boolean changed = (this.showErrorsGrid != showErrors);
        this.showErrorsGrid = showErrors;
        if (changed)
            notifyChange();
    }

    /**
     * Show errors under the cursor only
     */
    public void setShowErrorsCursor(boolean showErrorsCursor) {
        boolean changed = (this.showErrorsCursor != showErrorsCursor);
        this.showErrorsCursor = showErrorsCursor;
        if (changed)
            notifyChange();
    }

    /**
     * Toggle show errors across the grid
     */
    public void toggleShowErrorsGrid() {
        this.showErrorsGrid = !this.showErrorsGrid;
        notifyChange(true);
    }

    /**
     * Toggle show errors across under cursor
     */
    public void toggleShowErrorsCursor() {
        this.showErrorsCursor = !this.showErrorsCursor;
        notifyChange(true);
    }

    /**
     * Is showing errors across the whole grid
     */
    public boolean isShowErrorsGrid() {
        return this.showErrorsGrid;
    }

    /**
     * Is showing errors across under cursor
     */
    public boolean isShowErrorsCursor() {
        return this.showErrorsCursor;
    }

    /**
     * Is showing errors either or cursor or grid
     */
    public boolean isShowErrors() {
        return isShowErrorsGrid() || isShowErrorsCursor();
    }

    public void setSkipCompletedLetters(boolean skipCompletedLetters) {
        this.skipCompletedLetters = skipCompletedLetters;
    }

    public boolean isSkipCompletedLetters() {
        return skipCompletedLetters;
    }

    public boolean isFilledClueNum(int number, boolean isAcross) {
        Position start = (isAcross ?
                          this.acrossWordStarts.get(number) :
                          this.downWordStarts.get(number));

        if(start == null)
            return false;

        int range = this.getWordRange(start, isAcross);
        int across = start.across;
        int down = start.down;

        for (int i = 0; i < range; i++) {
            int newAcross = isAcross ? across + i : across;
            int newDown = isAcross ? down : down + i;

            if (this.boxes[newAcross][newDown].isBlank())
                return false;
        }

        return true;
    }

    public Box[] getWordBoxes(int number, boolean isAcross) {
        Position start = isAcross ? this.acrossWordStarts.get(number) : this.downWordStarts.get(number);
        if(start == null) {
            return new Box[0];
        }
        int range = this.getWordRange(start, isAcross);
        int across = start.across;
        int down = start.down;
        Box[] result = new Box[range];

        for (int i = 0; i < result.length; i++) {
            int newAcross = across;
            int newDown = down;

            if (isAcross) {
                newAcross += i;
            } else {
                newDown += i;
            }

            result[i] = this.boxes[newAcross][newDown];
        }

        return result;
    }

    public int getWordRange(Position start, boolean across) {
        if (across) {
            int col = start.across;
            Box b;

            do {
                b = null;

                int checkCol = col + 1;

                try {
                    col++;
                    b = this.getBoxes()[checkCol][start.down];
                } catch (RuntimeException ignored) {
                }
            } while (b != null);

            return col - start.across;
        } else {
            int row = start.down;
            Box b;

            do {
                b = null;

                int checkRow = row + 1;

                try {
                    row++;
                    b = this.getBoxes()[start.across][checkRow];
                } catch (RuntimeException ignored) {
                }
            } while (b != null);

            return row - start.down;
        }
    }

    public int getWordRange() {
        return getWordRange(this.getCurrentWordStart(), this.isAcross());
    }

    /**
     * Handler for the backspace key.  Uses the following algorithm:
     * -If current box is empty, move back one character.  If not, stay still.
     * -Delete the letter in the current box.
     */
    public Word deleteLetter() {
        Box currentBox = this.getCurrentBox();
        Word wordToReturn = this.getCurrentWord();

        pushNotificationDisabled();


        if (currentBox.isBlank() || isDontDeleteCurrent()) {
            wordToReturn = this.previousLetter();
            currentBox = this.boxes[this.highlightLetter.across][this.highlightLetter.down];
        }


        if (!isDontDeleteCurrent()) {
            currentBox.setBlank();
        }

        popNotificationDisabled();

        notifyChange();

        return wordToReturn;
    }

    public void deleteScratchLetter() {
        Box currentBox = this.getCurrentBox();

        pushNotificationDisabled();

        if (currentBox.isBlank()) {
            Note note = this.getNote();
            if (note != null) {
                int pos = this.across ? currentBox.getAcrossPosition() : currentBox.getDownPosition();
                String response = this.getCurrentWordResponse();
                if (pos >= 0 && pos < response.length())
                    note.deleteScratchLetterAt(pos);
            }
        }

        this.previousLetter();

        popNotificationDisabled();
        notifyChange();
    }

    /**
     * Returns true if the current letter should not be deleted
     *
     * E.g. because it is correct and show errors is show
     */
    private boolean isDontDeleteCurrent() {
        Box currentBox = getCurrentBox();

        // Prohibit deleting correct letters
        boolean skipCorrect
            = (preserveCorrectLettersInShowErrors &&
               currentBox.getResponse() == currentBox.getSolution() &&
               this.isShowErrors());

        // Don't delete letters from crossing words
        Box adjacentLeft = getAdjacentBoxLeft();
        Box adjacentRight = getAdjacentBoxRight();
        boolean skipAdjacent
            = (dontDeleteCrossing &&
               ((adjacentLeft != null && !adjacentLeft.isBlank()) ||
                (adjacentRight != null && !adjacentRight.isBlank())));

        return skipCorrect || skipAdjacent;
    }

    /**
     * Ignored if clueNumber does not exist
     */
    public void jumpToClue(int clueNumber, boolean across) {
        try {
            pushNotificationDisabled();

            Position pos = null;

            if (across) {
                pos = this.acrossWordStarts.get(clueNumber);
            } else {
                pos = this.downWordStarts.get(clueNumber);
            }

            if (pos != null) {
                this.setHighlightLetter(pos);
                this.setAcross(across);
            }

            popNotificationDisabled();

            if (pos != null)
                notifyChange();
        } catch (Exception e) {
        }
    }

    public Word moveDown() {
        return this.moveDown(false);
    }

    public Position moveDown(Position original, boolean skipCompleted) {
        Position next = new Position(original.across, original.down + 1);
        Box value = this.getBoxes()[next.across][next.down];

        if ((value == null) || skipCurrentBox(value, skipCompleted)) {
            try {
                next = moveDown(next, skipCompleted);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }

        return next;
    }

    public Word moveDown(boolean skipCompleted) {
        Word w = this.getCurrentWord();

        //noinspection EmptyCatchBlock
        try {
            Position newPos = this.moveDown(this.getHighlightLetter(), skipCompleted);
            this.setHighlightLetter(newPos);
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        return w;
    }

    public Position moveLeft(Position original, boolean skipCompleted) {
        Position next = new Position(original.across - 1, original.down);
        Box value = this.getBoxes()[next.across][next.down];

        if ((value == null) || skipCurrentBox(value, skipCompleted)) {
            //noinspection EmptyCatchBlock
            try {
                next = moveLeft(next, skipCompleted);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }

        return next;
    }

    public Word moveLeft(boolean skipCompleted) {
        Word w = this.getCurrentWord();

        //noinspection EmptyCatchBlock
        try {
            Position newPos = this.moveLeft(this.getHighlightLetter(), skipCompleted);
            this.setHighlightLetter(newPos);
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        return w;
    }

    public Word moveLeft() {
        return moveLeft(false);
    }

    public Word moveRight() {
        return moveRight(false);
    }

    public Position moveRight(Position original, boolean skipCompleted) {
        Position next = new Position(original.across + 1, original.down);
        Box value = this.getBoxes()[next.across][next.down];

        if ((value == null) || skipCurrentBox(value, skipCompleted)) {
            try {
                next = moveRight(next, skipCompleted);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }

        return next;
    }

    public Word moveRight(boolean skipCompleted) {
        Word w = this.getCurrentWord();

        try {
            Position newPos = this.moveRight(this.getHighlightLetter(), skipCompleted);
            this.setHighlightLetter(newPos);
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        return w;
    }

    public Position moveUp(Position original, boolean skipCompleted) {
        Position next = new Position(original.across, original.down - 1);
        Box value = this.getBoxes()[next.across][next.down];

        if ((value == null) || skipCurrentBox(value, skipCompleted)) {
            try {
                next = moveUp(next, skipCompleted);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }

        return next;
    }

    public Word moveUp() {
        return moveUp(false);
    }

    public Word moveUp(boolean skipCompleted) {
        Word w = this.getCurrentWord();

        try {
            Position newPos = this.moveUp(this.getHighlightLetter(), skipCompleted);
            this.setHighlightLetter(newPos);
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        return w;
    }

    public Word nextLetter(boolean skipCompletedLetters) {
        return this.movementStrategy.move(this, skipCompletedLetters);
    }

    public Word nextLetter() {
        return nextLetter(this.skipCompletedLetters);
    }

    /**
     * Jump to next word, regardless of movement strategy
     */
    public Word nextWord() {
        Word previous = this.getCurrentWord();

        Position p = this.getHighlightLetter();

        int newAcross = p.across;
        int newDown = p.down;

        if (previous.across) {
            newAcross = (previous.start.across + previous.length) - 1;
        } else {
            newDown = (previous.start.down + previous.length) - 1;
        }

        Position newPos = new Position(newAcross, newDown);

        if (!newPos.equals(p)) {
            pushNotificationDisabled();
            this.setHighlightLetter(newPos);
            popNotificationDisabled();
        }

        MovementStrategy.MOVE_NEXT_CLUE.move(this, false);

        return previous;
    }

    public Word playLetter(char letter) {
        Box b = this.boxes[this.highlightLetter.across][this.highlightLetter.down];

        if (b == null) {
            return null;
        }

        if (preserveCorrectLettersInShowErrors && b.getResponse() == b.getSolution() && isShowErrors()) {
            // Prohibit replacing correct letters
            return this.getCurrentWord();
        } else {
            pushNotificationDisabled();
            b.setResponse(letter);
            b.setResponder(this.responder);
            Word next = this.nextLetter();
            popNotificationDisabled();

            notifyChange();

            return next;
        }
    }

    public void playScratchLetter(char letter) {
        Box b = this.boxes[this.highlightLetter.across][this.highlightLetter.down];

        if (b == null) {
            return;
        }

        pushNotificationDisabled();

        Note note = this.getNote();
        String response = this.getCurrentWordResponse();

        // Create a note for this clue if we don't already have one
        if (note == null) {
            note = new Note(response.length());
            Clue c = this.getClue();
            this.puzzle.setNote(c.getNumber(), note, this.across);
        }

        // Update the scratch text
        int pos = this.across ? b.getAcrossPosition() : b.getDownPosition();
        if (pos >= 0 && pos < response.length())
            note.setScratchLetter(pos, letter);

        this.nextLetter();
        popNotificationDisabled();

        notifyChange();
    }

    public Word previousLetter() {
        return this.movementStrategy.back(this);
    }

    /**
     * Moves to start of previous word regardless of movement strategy
     */
    public Word previousWord() {
        Word previous = this.getCurrentWord();

        Position p = this.getHighlightLetter();

        int newAcross = p.across;
        int newDown = p.down;

        pushNotificationDisabled();

        if (previous.across && p.across != previous.start.across) {
            this.setHighlightLetter(
                new Position(previous.start.across, p.down)
            );
        } else if (!previous.across && p.down != previous.start.down) {
            this.setHighlightLetter(
                new Position(p.across, previous.start.down)
            );
        }

        MovementStrategy.MOVE_NEXT_CLUE.back(this);

        popNotificationDisabled();

        Word current = this.getCurrentWord();
        this.setHighlightLetter(new Position(current.start.across, current.start.down));

        return previous;
    }

    public Position revealLetter() {
        Box b = this.boxes[this.highlightLetter.across][this.highlightLetter.down];

        if ((b != null) && (b.getSolution() != b.getResponse())) {
            b.setCheated(true);
            b.setResponse(b.getSolution());

            notifyChange();

            return this.highlightLetter;
        }

        return null;
    }

    /**
     * Reveals the correct answers for any "red" squares on the board.
     *
     * This covers hidden and visible incorrect responses, as well as squares that are marked as
     * "cheated" from previously erased incorrect responses.
     *
     * @return
     */
    public List<Position> revealErrors() {
        ArrayList<Position> changes = new ArrayList<Position>();

        for (int across = 0; across < this.boxes.length; across++) {
            for (int down = 0; down < this.boxes[across].length; down++) {
                Box b = this.boxes[across][down];
                if (b == null) { continue; }

                if (b.isCheated() ||
                        (!b.isBlank() && (b.getSolution() != b.getResponse()))) {
                    b.setCheated(true);
                    b.setResponse(b.getSolution());
                    changes.add(new Position(across, down));
                }
            }
        }

        notifyChange(true);

        return changes;
    }

    public List<Position> revealPuzzle() {
        ArrayList<Position> changes = new ArrayList<Position>();

        for (int across = 0; across < this.boxes.length; across++) {
            for (int down = 0; down < this.boxes[across].length; down++) {
                Box b = this.boxes[across][down];

                if ((b != null) && (b.getSolution() != b.getResponse())) {
                    b.setCheated(true);
                    b.setResponse(b.getSolution());
                    changes.add(new Position(across, down));
                }
            }
        }

        notifyChange(true);

        return changes;
    }

    public List<Position> revealWord() {
        ArrayList<Position> changes = new ArrayList<Position>();
        Position oldHighlight = this.highlightLetter;
        Word w = this.getCurrentWord();

        pushNotificationDisabled();

        if (!oldHighlight.equals(w.start))
            this.setHighlightLetter(w.start);

        for (int i = 0; i < w.length; i++) {
            Position p = revealLetter();

            if (p != null) {
                changes.add(p);
            }

            nextLetter(false);
        }

        popNotificationDisabled();

        this.setHighlightLetter(oldHighlight);

        return changes;
    }

    public boolean skipCurrentBox(Box b, boolean skipCompleted) {
        return skipCompleted && !b.isBlank() &&
        (!this.isShowErrors() || (b.getResponse() == b.getSolution()));
    }

    public Word toggleDirection() {
        Word w = this.getCurrentWord();
        Position cur = getHighlightLetter();
        int x = cur.across;
        int y = cur.down;

        if ((across && boxes[x][y].isPartOfDown()) ||
            (!across && boxes[x][y].isPartOfAcross())) {
            this.setAcross(!this.isAcross());
        }

        return w;
    }

    public void addListener(PlayboardListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PlayboardListener listener) {
        listeners.remove(listener);
    }

    private void notifyChange() { notifyChange(false); }

    private void notifyChange(boolean wholeBoard) {
        if (notificationDisabledDepth == 0) {
            updateHistory();

            Word currentWord = getCurrentWord();
            for (PlayboardListener listener : listeners) {
                listener.onPlayboardChange(
                    wholeBoard, currentWord, previousWord
                );
            }
            previousWord = currentWord;
        }
    }

    private void pushNotificationDisabled() {
        notificationDisabledDepth += 1;
    }

    private void popNotificationDisabled() {
        if (notificationDisabledDepth > 0)
            notificationDisabledDepth -= 1;
    }

    public static class Position implements Serializable {
        public int across;
        public int down;

        protected Position(){

        }

        public Position(int across, int down) {
            this.down = down;
            this.across = across;
        }

        @Override
        public boolean equals(Object o) {
            if ((o == null) || (o.getClass() != this.getClass())) {
                return false;
            }

            Position p = (Position) o;

            return ((p.down == this.down) && (p.across == this.across));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new int[] {across, down});
        }

        @Override
        public String toString() {
            return "[" + this.across + " x " + this.down + "]";
        }
    }

    public static class Word implements Serializable {
        public Position start;
        public boolean across;
        public int length;

        public boolean checkInWord(int across, int down) {
            int ranging = this.across ? across : down;
            boolean offRanging = this.across ? (down == start.down) : (across == start.across);

            int startPos = this.across ? start.across : start.down;

            return (offRanging && (startPos <= ranging) && ((startPos + length) > ranging));
        }

        @Override
        public boolean equals(Object o) {
            if (o.getClass() != Word.class) {
                return false;
            }

            Word check = (Word) o;

            return check.start.equals(this.start) && (check.across == this.across) && (check.length == this.length);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = (29 * hash) + ((this.start != null) ? this.start.hashCode() : 0);
            hash = (29 * hash) + (this.across ? 1 : 0);
            hash = (29 * hash) + this.length;

            return hash;
        }
    }

    private void updateHistory() {
        if (puzzle != null) {
            Clue c = getClue();
            if (c != null)
                puzzle.updateHistory(c.getNumber(), c.getIsAcross());
        }
    }

    /**
     * Playboard listeners will be updated when the highlighted letter
     * changes or the contents of a box changes.
     *
     * TODO: what about notes in scratch?
     */
    public interface PlayboardListener {
        /**
         * Notify that something has changed on the board
         *
         * currentWord and previousWord are the selected words since the
         * last notification. These will be where changes are.
         *
         * @param wholeBoard true if change affects whole board
         * @param currentWord the currently selected word
         * @param previousWord the word selected in the last
         * notification (may be null)
         */
        public void onPlayboardChange(
            boolean wholeBoard, Word currentWord, Word previousWord
        );
    }
}
