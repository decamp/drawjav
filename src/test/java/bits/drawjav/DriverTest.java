/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.File;

import bits.drawjav.old.*;
import bits.drawjav.video.VideoWindow;
import bits.drawjav.video.VideoTexture;
import bits.jav.Jav;
import bits.jav.util.Rational;
import bits.microtime.PlayController;
import bits.microtime.Ticker;


/**
 * @author decamp
 */
public class DriverTest {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );

    
    public static void main( String[] args ) throws Exception {
        testRealtime();
//        testSynced();
//        testMultiRealtime();
//        testMultiSynced();
    }
    
    
    
    static void testRealtime() throws Exception {
        File file = TEST_FILE;

        final MemoryManager mem       = new PoolMemoryManager();
        final PlayController playCont = PlayController.createAuto();
        final FormatReader decoder    = FormatReader.openFile( file, true, 0L, mem );
        final RealtimeDriver driver   = new RealtimeDriver( playCont.clock(), decoder, mem, null );
        final VideoTexture tex        = new VideoTexture();
        
        Stream sh = decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        StreamFormat fmt = StreamFormat.createVideo( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        driver.openVideoStream( decoder, sh, fmt, tex );
        driver.seekWarmupMicros( 3000000L );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        new VideoWindow( null, tex );

        playCont.control().clockSeek( 0 );
        driver.start();
        playCont.control().clockStart();

        try {
            Thread.sleep( 2000L );
            System.out.println( "STOP" );
            playCont.control().clockStop();
            Thread.sleep( 2000L );
            System.out.println("SEEK");
            playCont.control().clockSeek( 100000L );
            Thread.sleep( 2000L );
            System.out.println("PLAY");
            playCont.control().clockStart();
//            
//            while( true ) {
//                Thread.sleep( 5000L );
//                System.out.println("SEEK");
//                playCont.control().seek( 10000000L );
//            }
        } catch( Exception ex ) {}
    }
    

    static void testSynced() throws Exception {
        File file = TEST_FILE;

        final MemoryManager mem       = new PoolMemoryManager();
        final PlayController playCont = PlayController.createStepping( 0, 1000000 / 30 );
        final FormatReader decoder    = FormatReader.openFile( file, true, 0L, mem );
        final SyncedDriver driver     = new SyncedDriver( mem, playCont.clock(), decoder );
        final VideoTexture tex        = new VideoTexture();
        
        Stream sh = decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        StreamFormat fmt = StreamFormat.createVideo( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        driver.openVideoStream( decoder, sh, fmt, tex );
        driver.seekWarmupMicros( 3000000L );

        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);

        final Ticker update = new Ticker() {
            public void tick() {
                playCont.tick();
                driver.tick();
                driver.tick();
            }
        };
        
        new VideoWindow( update, tex );
        
        try {
            Thread.sleep( 1000L );
            System.out.println("SEEK");
            playCont.control().clockSeek( 5000000L );
            System.out.println("PLAY");
            playCont.control().clockStart();
            
            while( true ) {
                Thread.sleep( 5000L );
                System.out.println("SEEK");
                playCont.control().clockSeek( 3000000L );
            }
        } catch( Exception ex ) {}
        
        driver.close();
    }
    
    
    static void testMultiRealtime() throws Exception {
        File file1 = new File( "../../ext/video.mp4" );
        File file2 = new File( "../../ext/video.ts" );

        System.out.println( new File( "." ).getAbsolutePath() );
        System.out.println( file1.getAbsolutePath() );

        final MemoryManager mem           = new PoolMemoryManager();
        final PlayController playCont     = PlayController.createAuto();
        final FormatReader decoder1       = FormatReader.openFile( file1, true, 0L, mem );
        final FormatReader decoder2       = FormatReader.openFile( file2, true, 0L, mem );
        final OneThreadMultiDriver driver = new OneThreadMultiDriver( mem, playCont.clock(), null );
        final VideoTexture tex1           = new VideoTexture();
        final VideoTexture tex2           = new VideoTexture();

        StreamFormat fmt = StreamFormat.createVideo(-1, -1, Jav.AV_PIX_FMT_BGRA, new Rational(1, 1) );
        driver.openVideoStream( decoder1, decoder1.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex1 );
        driver.openVideoStream( decoder2, decoder2.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex2 );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        
        new VideoWindow( null, tex1, tex2 );
        
        playCont.control().clockSeek( 0L );
        driver.start();
        playCont.control().clockStart();
        
        try {
            Thread.sleep( 1000L );
            System.out.println("SEEK");
            playCont.control().clockSeek( 0L );
            System.out.println("PLAY");
            playCont.control().clockStart();
            
            while( true ) {
                Thread.sleep( 5000L );
                System.out.println("SEEK");
                playCont.control().clockSeek( 12000000L );
                Thread.sleep( 2000L );
                playCont.control().clockStop();
                System.out.println( "PAUSE" );
                Thread.sleep( 2000L );
                playCont.control().clockStart();
                System.out.println( "PLAY" );
            }
        } catch(Exception ignore) {}
    }

    
    static void testMultiSynced() throws Exception {
        File file1 = new File( "../../ext/video.mp4" );
        File file2 = new File( "../../ext/video.ts" );

        final MemoryManager mem        = new PoolMemoryManager();
        final PlayController playCont  = PlayController.createStepping( 0L, 1000000L / 30L );
        final FormatReader decoder1    = FormatReader.openFile( file1, true, 0L, mem );
        final FormatReader decoder2    = FormatReader.openFile( file2, true, 0L, mem );
        final MultiSyncedDriver driver = new MultiSyncedDriver( mem, playCont.clock() );
        final VideoTexture tex1        = new VideoTexture();
        final VideoTexture tex2        = new VideoTexture();
        
        StreamFormat fmt = StreamFormat.createVideo( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        driver.openVideoStream( decoder1, decoder1.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex1 );
        driver.openVideoStream( decoder2, decoder2.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex2 );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        final Ticker update = new Ticker() {
            public void tick() {
                playCont.tick();
                driver.tick();
                driver.tick();
            }
        };
        
        new VideoWindow( update, tex1, tex2 );
        
        playCont.control().clockSeek( 0L );
        driver.start();
        playCont.control().clockStart();
        
    }
}
