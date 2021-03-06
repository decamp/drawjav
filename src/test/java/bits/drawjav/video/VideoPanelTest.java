/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.io.*;
import javax.swing.JFrame;

import bits.drawjav.*;
import bits.drawjav.old.RealtimeDriver;
import bits.drawjav.old.Sink;
import bits.jav.Jav;
import bits.microtime.Frac;
import bits.microtime.PlayController;


/**
 * @author decamp
 */
public class VideoPanelTest {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );

    
    public static void main( String[] args ) throws Exception {
        test1();
    }
    
    
    
    static void test1() throws Exception {
        JFrame frame = new JFrame();
        frame.setSize( 800, 600 );
        frame.setLocationRelativeTo( null );
        
        OldVideoPanel panel = new OldVideoPanel();
        frame.setContentPane( panel );
        frame.setVisible( true );
        
        File file = TEST_FILE;
        PlayController playCont = PlayController.createAuto();
        FormatReader decoder   = FormatReader.openFile( file, true, 0L, null );
        decoder.openStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );
        
        RealtimeDriver driver = new RealtimeDriver( playCont.clock(), decoder, null, null );
        driver.seekWarmupMicros( 3000000L );
        driver.openVideoStream( null, decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), null, panel );
//                                new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new bits.jav.util.Rational( 1, 1 ) ), panel );

        playCont.control().clockRate( new Frac( 1, 1 ) );
        playCont.control().clockSeek( 100000L );
        driver.start();
        playCont.control().clockStart();
        
//        new DumbDriver( decoder, panel );
    }
    
    

    @SuppressWarnings( { "unchecked", "rawtypes", "unused" } )
    private static final class DumbDriver extends Thread {

        private final PacketReader mSource;
        private final Sink<Packet> mSink;

        public DumbDriver( PacketReader source, Sink sink ) {
            mSource = source;
            mSink = sink;
            start();
        }


        public void run() {
            while( true ) {
                try {
                    Thread.sleep( 25L );
                } catch( InterruptedException ignored ) {
                }

                while( true ) {
                    try {
                        Packet packet = mSource.readNext();
                        if( packet != null ) {
                            mSink.consume( packet );
                            break;
                        }
                    } catch( IOException ex ) {
                        return;
                    }
                }
            }
        }
    }

}

