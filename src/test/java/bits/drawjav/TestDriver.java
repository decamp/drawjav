/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import static javax.media.opengl.GL.*;

import java.io.File;

import javax.media.opengl.*;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;

import bits.draw3d.*;
import bits.drawjav.video.VideoTexture;
import bits.draw3d.util.LimitAnimator;
import bits.jav.Jav;
import bits.jav.util.Rational;
import bits.microtime.PlayController;


/**
 * @author decamp
 */
public class TestDriver {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );

    
    public static void main( String[] args ) throws Exception {
        testRealtime();
//        testSynced();
//        testMultiRealtime();
//        testMultiSynced();
    }
    
    
    
    static void testRealtime() throws Exception {
        File file = TEST_FILE;

        final MemoryManager mem       = new PoolMemoryManager( -1, 1024 * 1024 * 16, -1, 1024 * 1024 * 256 );
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
        new VideoFrame( null, tex );

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

        final MemoryManager mem       = new PoolMemoryManager( -1, 1024 * 1024 * 16, -1, 1024 * 1024 * 256 );
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

        final DrawNode update = new DrawNodeAdapter() {
            public void pushDraw( DrawEnv d ) {
                playCont.tick();
                driver.tick();
                driver.tick();
            }
        };
        
        new VideoFrame( update, tex );
        
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

        final MemoryManager mem           = new PoolMemoryManager( -1, 1024 * 1024 * 16, -1, 1024 * 1024 * 256 );
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
        
        new VideoFrame( null, tex1, tex2 );
        
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

        final MemoryManager mem        = new PoolMemoryManager( -1, 1024 * 1024 * 16, -1, 1024 * 1024 * 256 );
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
        final DrawNode update = new DrawNodeAdapter() {
            public void pushDraw( DrawEnv d ) {
                playCont.tick();
                driver.tick();
                driver.tick();
            }
        };
        
        new VideoFrame( update, tex1, tex2 );
        
        playCont.control().clockSeek( 0L );
        driver.start();
        playCont.control().clockStart();
        
    }

    
    private static class VideoFrame extends JFrame implements GLEventListener {

        private final DrawNode   mUpdateNode;
        private final DrawUnit[] mTexs;
        private final GLCanvas   mCanvas;
        private final DrawEnv mEnv = new DrawEnv();

        public VideoFrame( DrawNode updateNode, DrawUnit... texs ) {
            mUpdateNode = updateNode;
            mTexs = texs;

            GLProfile profile = GLProfile.get( GLProfile.GL3 );
            GLCapabilities glc = new GLCapabilities( profile );
            mCanvas = new GLCanvas( glc );

            setSize( 1024, 768 );
            setLocationRelativeTo( null );
            setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
            getContentPane().add( mCanvas );

            mCanvas.addGLEventListener( this );

            setVisible( true );
            new LimitAnimator( mCanvas ).start();
        }


        public void display( GLAutoDrawable gld ) {
            final DrawEnv d = mEnv;
            d.init( gld, null );
            GL gl = d.mGl;

            if( mUpdateNode != null ) {
                mUpdateNode.pushDraw( d );
                mUpdateNode.popDraw( d );
            }

            d.mProj.identity();
            d.mView.identity();
            d.mView.setOrtho( 0, 1, 0, 1, -1, 1 );
            gl.glClearColor( 0, 0, 0, 0 );
            gl.glClear( GL_COLOR_BUFFER_BIT );
            DrawStream s = d.drawStream();
            s.config( true, true, false );
            s.color( 1, 1, 1, 1 );
            gl.glActiveTexture( GL_TEXTURE0 );

            for( int i = 0; i < mTexs.length; i++ ) {
                float y0 = (float)i / mTexs.length;
                float y1 = (float)(i + 1) / mTexs.length;
                mTexs[i].bind( d );

                s.beginQuads();
                s.tex( 0, 1 );
                s.vert( 0, y0 );
                s.tex( 1, 1 );
                s.vert( 1, y0 );
                s.tex( 1, 0 );
                s.vert( 1, y1 );
                s.tex( 0, 0 );
                s.vert( 0, y1 );
                s.end();
            }
        }

        public void init( GLAutoDrawable arg0 ) {}

        @Override
        public void dispose( GLAutoDrawable drawable ) {}

        public void reshape( GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4 ) {}

    }

}
