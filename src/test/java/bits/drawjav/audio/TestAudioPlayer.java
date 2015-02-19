package bits.drawjav.audio;

import bits.drawjav.*;
import bits.drawjav.pipe.AudioPlayer;
import bits.jav.Jav;

import java.io.File;


/**
 * @author Philip DeCamp
 */
public class TestAudioPlayer {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );

    public static void main( String[] args ) throws Exception {
        testPlay();
    }


    static void testPlay() throws Exception {
        File file = TEST_FILE;
        MemoryManager mem = new PoolMemoryManager( 128, -1, 0, 0 );
        FormatReader reader = FormatReader.openFile( file );
        StreamHandle stream = reader.stream( Jav.AVMEDIA_TYPE_AUDIO, 0 );
        reader.openStream( stream );
        AudioPlayer player = new AudioPlayer( mem, reader );
    }

}
