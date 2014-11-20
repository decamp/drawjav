package bits.drawjav;

import static javax.media.opengl.GL.*;

import java.io.File;

import javax.media.opengl.*;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;

import bits.draw3d.*;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoTexture;
import bits.glui.util.LimitAnimator;
import bits.jav.Jav;
import bits.jav.util.Rational;
import bits.microtime.PlayController;


/**
 * @author decamp
 */
public class DriverTest {

    private static final File TEST_FILE = new File( "../../../jav/src/test/resources/test.mp4" );

    
    public static void main( String[] args ) throws Exception {
//        testRealtime();
//        testSynced();
//        testMultiRealtime();
        testMultiSynced();
    }
    
    
    
    static void testRealtime() throws Exception {
        File file = TEST_FILE;
        
        final PlayController playCont = PlayController.createAuto();
        final FormatDecoder decoder   = FormatDecoder.openFile( file, true, 0L );
        final RealtimeDriver driver   = new RealtimeDriver( playCont, decoder, null );
        final VideoTexture tex        = new VideoTexture();
        
        StreamHandle sh = decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        PictureFormat fmt = new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        driver.openVideoStream( sh, fmt, tex ); 
        driver.seekWarmupMicros( 3000000L );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        new VideoFrame( null, tex );

        playCont.control().seek( 0000000L );
        driver.start();
        playCont.control().playStart();

        try {
            Thread.sleep( 2000L );
            System.out.println( "STOP" );
            playCont.control().playStop();
            Thread.sleep( 2000L );
            System.out.println("SEEK");
            playCont.control().seek( 100000L );
            Thread.sleep( 2000L );
            System.out.println("PLAY");
            playCont.control().playStart();
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
        
        final PlayController playCont = PlayController.createStepping( 0, 1000000 / 30 );
        final FormatDecoder decoder   = FormatDecoder.openFile( file, true, 0L );
        final SyncedDriver driver     = new SyncedDriver( playCont, decoder ); 
        final VideoTexture tex        = new VideoTexture();
        
        StreamHandle sh = decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 );
        PictureFormat fmt = new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        driver.openVideoStream( sh, fmt, tex );
        driver.seekWarmupMicros( 3000000L );

        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);

        final DrawNode update = new DrawNodeAdapter() {
            public void pushDraw( DrawEnv d ) {
                playCont.updateClocks();
                //System.out.println( playCont.clock().micros() );
                driver.pushDraw( d );
                driver.popDraw( d );
            }
        };
        
        new VideoFrame( update, tex );
        
        try {
            Thread.sleep( 1000L );
            System.out.println("SEEK");
            playCont.control().seek( 5000000L );
            System.out.println("PLAY");
            playCont.control().playStart();
            
            while( true ) {
                Thread.sleep( 5000L );
                System.out.println("SEEK");
                playCont.control().seek( 3000000L );
            }
        } catch( Exception ex ) {}
        
        driver.close();
    }
    
    
    static void testMultiRealtime() throws Exception {
        File file1 = new File( "../../ext/video.mp4" );
        File file2 = new File( "../../ext/video.ts" );
        
        final PlayController playCont  = PlayController.createAuto();
        final FormatDecoder decoder1   = FormatDecoder.openFile( file1, true, 0L );
        final FormatDecoder decoder2   = FormatDecoder.openFile( file2, true, 0L );
        final OneThreadMultiDriver driver = new OneThreadMultiDriver( playCont, null );
        final VideoTexture tex1        = new VideoTexture();
        final VideoTexture tex2        = new VideoTexture();
        
        driver.addSource( decoder1 );
        driver.addSource( decoder2 );
        
        PictureFormat fmt = new PictureFormat(-1, -1, Jav.AV_PIX_FMT_BGRA, new Rational(1, 1) );
        driver.openVideoStream( decoder1.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex1 );
        driver.openVideoStream( decoder2.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex2 );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        
        new VideoFrame( null, tex1, tex2 );
        
        playCont.control().seek( 0L );
        driver.start();
        playCont.control().playStart();
        
        try {
            Thread.sleep( 1000L );
            System.out.println("SEEK");
            playCont.control().seek( 0L );
            System.out.println("PLAY");
            playCont.control().playStart();
            
            while( true ) {
                Thread.sleep( 5000L );
                System.out.println("SEEK");
                playCont.control().seek( 10000000L );
            }
            
            
        }catch(Exception ex) {}
    }

    
    static void testMultiSynced() throws Exception {
        File file1 = new File( "../../ext/video.mp4" );
        File file2 = new File( "../../ext/video.ts" );
        
        final PlayController playCont  = PlayController.createStepping( 0L, 1000000L / 30L );
        final FormatDecoder decoder1   = FormatDecoder.openFile( file1, true, 0L );
        final FormatDecoder decoder2   = FormatDecoder.openFile( file2, true, 0L );
        final MultiSyncedDriver driver = new MultiSyncedDriver( playCont );
        final VideoTexture tex1        = new VideoTexture();
        final VideoTexture tex2        = new VideoTexture();
        
        driver.addSource( decoder1 );
        driver.addSource( decoder2 );

        PictureFormat fmt = new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        driver.openVideoStream( decoder1.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex1 );
        driver.openVideoStream( decoder2.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, tex2 );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        final DrawNode update = new DrawNodeAdapter() {
            public void pushDraw( DrawEnv d ) {
                playCont.updateClocks();
                driver.pushDraw( d );
                driver.popDraw( d );
            }
        };
        
        new VideoFrame( update, tex1, tex2 );
        
        playCont.control().seek( 0L );
        driver.start();
        playCont.control().playStart();
        
//        try {
//            Thread.sleep( 1000L );
//            System.out.println("SEEK");
//            playCont.control().seek( 5000000L );
//            System.out.println("PLAY");
//            playCont.control().playStart();
//            
//            while( true ) {
//                Thread.sleep( 5000L );
//                System.out.println("SEEK");
//                playCont.control().seek( 10000000L );
//            }
//        } catch(Exception ex) {}
        
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

        public void displayChanged( GLAutoDrawable arg0, boolean arg1, boolean arg2 ) {}

        public void init( GLAutoDrawable arg0 ) {}

        @Override
        public void dispose( GLAutoDrawable drawable ) {}

        public void reshape( GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4 ) {}

    }

}
