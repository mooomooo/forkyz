package app.crossword.yourealwaysbe.io.versions;

import app.crossword.yourealwaysbe.io.IO;
import app.crossword.yourealwaysbe.puz.Box;
import app.crossword.yourealwaysbe.puz.Playboard.Position;
import app.crossword.yourealwaysbe.puz.Puzzle;
import app.crossword.yourealwaysbe.puz.PuzzleMeta;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Logger;

// Adds percentFilled
public class IOVersion4 extends IOVersion3 {
    private static final Logger LOG = Logger.getLogger(IOVersion4.class.getCanonicalName());

    @Override
    public PuzzleMeta readMeta(DataInputStream dis) throws IOException {
        PuzzleMeta meta = new PuzzleMeta();
        meta.author = IO.readNullTerminatedString(dis);
        meta.source = IO.readNullTerminatedString(dis);
        meta.title = IO.readNullTerminatedString(dis);
        meta.date = new Date( dis.readLong() );
        meta.percentComplete = dis.readInt();
        meta.percentFilled = dis.readInt();
        meta.updatable = dis.read() == 1;
        meta.sourceUrl = IO.readNullTerminatedString(dis);
        int x = dis.readInt();
        int y = dis.readInt();
        meta.position = new Position(x, y);
        meta.across = dis.read() == 1;
        return meta;
    }

    @Override
    public void write(Puzzle puz, DataOutputStream dos) throws IOException {
        IO.writeNullTerminatedString(dos, puz.getAuthor());
        IO.writeNullTerminatedString(dos, puz.getSource());
        IO.writeNullTerminatedString(dos, puz.getTitle());
        dos.writeLong(puz.getDate() == null ? 0 : puz.getDate().getTime());
        dos.writeInt(puz.getPercentComplete());
        dos.writeInt(puz.getPercentFilled());
        dos.write(puz.isUpdatable() ? 1 : -1);
        IO.writeNullTerminatedString(dos, puz.getSourceUrl());
        Position p = puz.getPosition();
        if (p != null) {
            dos.writeInt(p.across);
            dos.writeInt(p.down);
        } else {
            dos.writeInt(0);
            dos.writeInt(0);
        }
        dos.write(puz.getAcross() ? 1 : -1);
        Box[][] boxes = puz.getBoxes();
        for(Box[] row : boxes ){
            for(Box b : row){
                if(b == null){
                    continue;
                }
                dos.writeBoolean(b.isCheated());
                IO.writeNullTerminatedString(dos, b.getResponder());
            }
        }
        dos.writeLong(puz.getTime());
    }

}
