package cogmac.drawjav;

import static javax.media.opengl.GL.*;

import java.io.File;

import javax.media.opengl.*;
import javax.swing.JFrame;

import cogmac.clocks.PlayController;
import cogmac.draw3d.nodes.*;
import cogmac.drawjav.video.VideoTexture;
import cogmac.jav.JavConstants;
import cogmac.jav.util.Rational;

import com.sun.opengl.util.Animator;

/**
 * @author decamp
 */
public class DriverTest {
    
    private static final File TEST_FILE = new File( "resources_ext/video.mp4" );
    
    
    public static void main( String[] args ) throws Exception {
        testMultiSynced();
    }
    
    
    
    static void testRealtime() throws Exception {
        File file = TEST_FILE;
        
        final PlayController playCont = PlayController.newAutoInstance();
        final FormatDecoder decoder   = FormatDecoder.openFile( file, true, 0L );
        final RealtimeDriver driver   = RealtimeDriver.newInstance( playCont, decoder, null ); 
        final VideoTexture tex        = new VideoTexture();
        
        StreamHandle sh = decoder.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 );
        driver.openVideoStream( sh, 
                                new PictureFormat(-1, -1, JavConstants.PIX_FMT_BGRA, new Rational(1, 1) ), 
                                tex );
        driver.seekWarmupMicros( 3000000L );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        
        VideoFrame frame = new VideoFrame( null, tex );
        playCont.control().seek( 2000000L );
        driver.start();
        playCont.control().playStart();

        try {
            Thread.sleep( 2000L );
            System.out.println( "STOP" );
            playCont.control().playStop();
            Thread.sleep( 2000L );
            System.out.println("SEEK");
            playCont.control().seek( 100000L );
            Thread.sleep( 100000L );
            System.out.println("PLAY");
            playCont.control().playStart();
//            
//            while( true ) {
//                Thread.sleep( 5000L );
//                System.out.println("SEEK");
//                playCont.control().seek( 10000000L );
//            }
            
            
        }catch(Exception ex) {}
    }
    
    
    static void testSynced() throws Exception {
        File file = TEST_FILE;
        
        final PlayController playCont = PlayController.newSteppingInstance( 0, 1000000 / 30 );
        final FormatDecoder decoder   = FormatDecoder.openFile( file, true, 0L );
        final SyncedDriver driver     = new SyncedDriver( playCont, decoder ); 
        final VideoTexture tex        = new VideoTexture();
        
        StreamHandle sh = decoder.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 );
        driver.openVideoStream( sh, 
                                new PictureFormat(-1, -1, JavConstants.PIX_FMT_BGRA, new Rational(1, 1) ), 
                                tex );
        driver.seekWarmupMicros( 3000000L );

        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        
        final DrawNode update = new DrawNodeAdapter() {
            public void pushDraw( GL gl ) {
                playCont.updateClocks();
                driver.pushDraw( gl );
                driver.popDraw( gl );
            }
        };
        
        VideoFrame frame = new VideoFrame( update, tex );
        
        try {
            Thread.sleep( 1000L );
            System.out.println("SEEK");
            playCont.control().seek( 5000000L );
            System.out.println("PLAY");
            playCont.control().playStart();
            
            while( true ) {
                Thread.sleep( 5000L );
                System.out.println("SEEK");
                playCont.control().seek( 10000000L );
            }
            
            
        }catch(Exception ex) {}
    }
    
    
    static void testMultiRealtime() throws Exception {
        File file1 = new File( "resources_ext/video.mp4" );
        File file2 = new File( "resources_ext/video.ts" );
        
        final PlayController playCont  = PlayController.newAutoInstance();
        final FormatDecoder decoder1   = FormatDecoder.openFile( file1, true, 0L );
        final FormatDecoder decoder2   = FormatDecoder.openFile( file2, true, 0L );
        final OneThreadMultiDriver driver = OneThreadMultiDriver.newInstance( playCont ); 
        final VideoTexture tex1        = new VideoTexture();
        final VideoTexture tex2        = new VideoTexture();
        
        driver.addSource( decoder1 );
        driver.addSource( decoder2 );
        
        driver.openVideoStream( decoder1.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 ), 
                                new PictureFormat(-1, -1, JavConstants.PIX_FMT_BGRA, new Rational(1, 1) ), 
                                tex1 );
        driver.openVideoStream( decoder2.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 ),
                                new PictureFormat( -1, -1, JavConstants.PIX_FMT_BGRA, new Rational( 1, 1 ) ),
                                tex2 );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        
        VideoFrame frame = new VideoFrame( null, tex1, tex2 );
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
        File file1 = new File( "resources_ext/video.mp4" );
        File file2 = new File( "resources_ext/video.ts" );
        
        final PlayController playCont  = PlayController.newSteppingInstance( 0L, 1000000L / 30L );
        final FormatDecoder decoder1   = FormatDecoder.openFile( file1, true, 0L );
        final FormatDecoder decoder2   = FormatDecoder.openFile( file2, true, 0L );
        final MultiSyncedDriver driver = MultiSyncedDriver.newInstance( playCont ); 
        final VideoTexture tex1        = new VideoTexture();
        final VideoTexture tex2        = new VideoTexture();
        
        driver.addSource( decoder1 );
        driver.addSource( decoder2 );
        
        driver.openVideoStream( decoder1.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 ), 
                                new PictureFormat(-1, -1, JavConstants.PIX_FMT_BGRA, new Rational( 1, 1 ) ), 
                                tex1 );
        
        driver.openVideoStream( decoder2.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 ),
                                new PictureFormat( -1, -1, JavConstants.PIX_FMT_BGRA, new Rational( 1, 1 ) ),
                                tex2 );
        
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        final DrawNode update = new DrawNodeAdapter() {
            public void pushDraw( GL gl ) {
                playCont.updateClocks();
                driver.pushDraw( gl );
                driver.popDraw( gl );
            }
        };
        
        VideoFrame frame = new VideoFrame( update, tex1, tex2 );
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

        private final DrawNode mUpdateNode;
        private final DrawNode[] mTexs;
        private final GLCanvas mCanvas;
        
        
        public VideoFrame( DrawNode updateNode, DrawNode... texs ) {
            mUpdateNode = updateNode;
            mTexs = texs;
            
            GLCapabilities glc = new GLCapabilities();
            mCanvas = new GLCanvas( glc );

            setSize( 1024, 768 );
            setLocationRelativeTo( null );
            setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
            getContentPane().add( mCanvas );
            
            mCanvas.addGLEventListener( this );
            
            setVisible(true);
            new Animator( mCanvas ).start();
        }
        
        
        public void display( GLAutoDrawable gld ) {
            GL gl = gld.getGL();

            if( mUpdateNode != null ) {
                mUpdateNode.pushDraw( gl );
                mUpdateNode.popDraw( gl );
            }
            
            gl.glMatrixMode( GL_PROJECTION );
            gl.glLoadIdentity();
            gl.glMatrixMode( GL_MODELVIEW );
            gl.glLoadIdentity();

            gl.glOrtho( 0.0, 1.0, 0.0, 1.0, -1.0, 1.0 );

            gl.glClearColor(0, 0, 0, 0);
            gl.glClear(GL_COLOR_BUFFER_BIT);
            gl.glColor4d(1,1,1,1);
            
            for( int i = 0; i < mTexs.length; i++ ) {
                double y0 = (double)i / mTexs.length;
                double y1 = (double)( i + 1 ) / mTexs.length;
                
                mTexs[i].pushDraw( gl );

                gl.glBegin(GL_QUADS);
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, y0);
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, y0);
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, y1);
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, y1);
                gl.glEnd();
                
                mTexs[i].popDraw( gl );
            }
        }
            
        public void displayChanged( GLAutoDrawable arg0, boolean arg1, boolean arg2 ) {}
        
        public void init( GLAutoDrawable arg0 ) {}
        
        public void reshape( GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4 ) {}

    }
    
}
