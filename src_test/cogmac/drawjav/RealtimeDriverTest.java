package cogmac.drawjav;

import static javax.media.opengl.GL.*;

import java.io.File;

import javax.media.opengl.*;
import javax.swing.JFrame;

import cogmac.clocks.PlayController;
import cogmac.jav.JavConstants;
import cogmac.jav.util.Rational;
import cogmac.javdraw.*;
import cogmac.javdraw.audio.AudioLinePlayer;
import cogmac.javdraw.video.VideoTexture;

import com.sun.opengl.util.Animator;

/**
 * @author decamp
 */
public class RealtimeDriverTest {
    
    
    public static void main( String[] args ) throws Exception {
        testMulti();
    }
    
    
    
    static void test1() throws Exception {
        File file = new File( "/Volumes/DATA2/bf/data/bluefin/CBS_2010-12-15T1801.ts" );
        
        final PlayController playCont = PlayController.newAutoInstance();
        final FormatDecoder decoder   = FormatDecoder.openFile( file, true, 0L );
        final RealtimeDriver driver   = RealtimeDriver.newInstance( playCont, decoder, null ); 
        final VideoTexture tex        = new VideoTexture();
        
        StreamHandle sh = decoder.stream( JavConstants.AVMEDIA_TYPE_VIDEO, 0 );
        driver.openVideoStream( sh, 
                                new PictureFormat(-1, -1, JavConstants.PIX_FMT_BGRA, new Rational(1, 1) ), 
                                tex );
        driver.seekWarmupMicros( 3000000L );
        driver.start();
        //sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
        //playCont.caster().addListener(player);
        //driver.openAudioStream(sh, sh.audioFormat(), player);
        
        GLCapabilities glc = new GLCapabilities();
        GLCanvas canvas    = new GLCanvas(glc);
        
        JFrame frame = new JFrame();
        frame.setSize(1024, 768);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(canvas);
        
        canvas.addGLEventListener(new GLEventListener() {

            public void display(GLAutoDrawable gld) {
                GL gl = gld.getGL();
                gl.glMatrixMode(GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glMatrixMode(GL_MODELVIEW);
                gl.glLoadIdentity();
                
                gl.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
                
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL_COLOR_BUFFER_BIT);
                
                gl.glColor4d(1,1,1,1);
                tex.pushDraw(gl);
                gl.glBegin(GL_QUADS);
                
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, 0);
                
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, 0);
                
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, 1);
                
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, 1);
                
                gl.glEnd();
                tex.popDraw(gl);
            }
            
            public void displayChanged(GLAutoDrawable arg0, boolean arg1, boolean arg2) {}

            public void init(GLAutoDrawable arg0) {}

            public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {}
        
        });        
        
        
        frame.setVisible(true);
        new Animator(canvas).start();
        
        
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
    
    
    
    static void testSynced() throws Exception {
        File file = new File( "/Volumes/DATA2/bf/data/bluefin/CBS_2010-12-15T1801.ts" );
        
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
        
        GLCapabilities glc = new GLCapabilities();
        GLCanvas canvas    = new GLCanvas(glc);
        
        JFrame frame = new JFrame();
        frame.setSize( 1024, 768 );
        frame.setLocationRelativeTo( null );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        frame.getContentPane().add( canvas );
        
        canvas.addGLEventListener(new GLEventListener() {

            public void display( GLAutoDrawable gld ) {
                GL gl = gld.getGL();
                
                playCont.updateClocks();
                driver.pushDraw( gl );
                driver.popDraw( gl );
                
                gl.glMatrixMode(GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glMatrixMode(GL_MODELVIEW);
                gl.glLoadIdentity();
                
                gl.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
                
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL_COLOR_BUFFER_BIT);
                
                gl.glColor4d(1,1,1,1);
                tex.pushDraw(gl);
                gl.glBegin(GL_QUADS);
                
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, 0);
                
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, 0);
                
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, 1);
                
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, 1);
                
                gl.glEnd();
                tex.popDraw(gl);
            }
            
            public void displayChanged(GLAutoDrawable arg0, boolean arg1, boolean arg2) {}

            public void init(GLAutoDrawable arg0) {}

            public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {}
        
        });        
        
        
        frame.setVisible(true);
        new Animator(canvas).start();
        
        
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
    
    
    static void testMulti() throws Exception {
        File file1 = new File( "/Volumes/DATA2/bf/data/bluefin/CBS_2010-12-15T1801.ts" );
        File file2 = new File( "/Volumes/DATA2/bf/data/bluefin/CNN_2010-12-15T1801.ts" );
        
        final PlayController playCont = PlayController.newAutoInstance();
        final FormatDecoder decoder1  = FormatDecoder.openFile( file1, true, 0L );
        final FormatDecoder decoder2  = FormatDecoder.openFile( file2, true, 0L );
        final OneThreadMultiDriver driver = OneThreadMultiDriver.newInstance( playCont );
        driver.addSource( decoder1 );
        driver.addSource( decoder2 );
        final VideoTexture tex1 = new VideoTexture();
        final VideoTexture tex2 = new VideoTexture();
        
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
        
        GLCapabilities glc = new GLCapabilities();
        GLCanvas canvas    = new GLCanvas(glc);
        
        JFrame frame = new JFrame();
        frame.setSize(1024, 768);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(canvas);
        
        canvas.addGLEventListener(new GLEventListener() {

            public void display(GLAutoDrawable gld) {
                GL gl = gld.getGL();
                gl.glMatrixMode(GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glMatrixMode(GL_MODELVIEW);
                gl.glLoadIdentity();
                
                gl.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
                
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL_COLOR_BUFFER_BIT);
                
                gl.glColor4d(1,1,1,1);
                
                tex1.pushDraw( gl );
                gl.glBegin(GL_QUADS);
                
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, 0);
                
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, 0);
                
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, 0.5);
                
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, 0.5);
                
                gl.glEnd();
                tex1.popDraw(gl);
                
                tex2.pushDraw( gl );
                gl.glBegin(GL_QUADS);
                
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, 0.5);
                
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, 0.5);
                
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, 1);
                
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, 1);
                
                gl.glEnd();
                tex2.popDraw(gl);
            }
            
            public void displayChanged(GLAutoDrawable arg0, boolean arg1, boolean arg2) {}

            public void init(GLAutoDrawable arg0) {}

            public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {}
        
        });        
        
        
        frame.setVisible(true);
        new Animator(canvas).start();
        
        
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

    
    static void testMultiSynced() throws Exception {
        File file1 = new File( "/Volumes/DATA2/bf/data/bluefin/CBS_2010-12-15T1801.ts" );
        File file2 = new File( "/Volumes/DATA2/bf/data/bluefin/CNN_2010-12-15T1801.ts" );
        
        final PlayController playCont = PlayController.newSteppingInstance( 0L, 1000000L / 30L );
        final FormatDecoder decoder1  = FormatDecoder.openFile( file1, true, 0L );
        final FormatDecoder decoder2  = FormatDecoder.openFile( file2, true, 0L );
        final MultiSyncedDriver driver = MultiSyncedDriver.newInstance( playCont );
        driver.addSource( decoder1 );
        driver.addSource( decoder2 );
        final VideoTexture tex1 = new VideoTexture();
        final VideoTexture tex2 = new VideoTexture();
        
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
        
        GLCapabilities glc = new GLCapabilities();
        GLCanvas canvas    = new GLCanvas(glc);
        
        JFrame frame = new JFrame();
        frame.setSize(1024, 768);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(canvas);
        
        canvas.addGLEventListener(new GLEventListener() {

            public void display(GLAutoDrawable gld) {
                GL gl = gld.getGL();
                
                playCont.updateClocks();
                driver.pushDraw( gl );
                driver.popDraw( gl );
                
                gl.glMatrixMode(GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glMatrixMode(GL_MODELVIEW);
                gl.glLoadIdentity();
                
                gl.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
                
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL_COLOR_BUFFER_BIT);
                
                gl.glColor4d(1,1,1,1);
                tex1.pushDraw( gl );
                gl.glBegin(GL_QUADS);
                
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, 0);
                
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, 0);
                
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, 0.5);
                
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, 0.5);
                
                gl.glEnd();
                tex1.popDraw(gl);
                
                tex2.pushDraw( gl );
                gl.glBegin(GL_QUADS);
                
                gl.glTexCoord2d(0, 1);
                gl.glVertex2d(0, 0.5);
                
                gl.glTexCoord2d(1, 1);
                gl.glVertex2d(1, 0.5);
                
                gl.glTexCoord2d(1, 0);
                gl.glVertex2d(1, 1);
                
                gl.glTexCoord2d(0, 0);
                gl.glVertex2d(0, 1);
                
                gl.glEnd();
                tex2.popDraw(gl);
            }
            
            public void displayChanged(GLAutoDrawable arg0, boolean arg1, boolean arg2) {}

            public void init(GLAutoDrawable arg0) {}

            public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {}
        
        });        
        
        
        frame.setVisible(true);
        new Animator(canvas).start();
        
        
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
    
    
    
    
//    
//    static void test2() throws Exception {
//        File file = new File("/workspace/decamp/code/gopdebate/resources_ext/oct18/oct18_debate_full.ts");
//        
//        final PlayController playCont = PlayController.newSteppingInstance(0, 1000000L / 30L);
//        final FormatDecoder decoder   = FormatDecoder.openFile(file, true, 0L);
//        final SyncedDriver driver     = SyncedDriver.newInstance(playCont, decoder); 
//        final VideoTexture tex        = new VideoTexture();
//        
//        StreamHandle sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_VIDEO);
//        driver.openVideoStream(sh, 0, 0, new PictureFormat(-1, -1, JavConstants.PIX_FMT_BGRA, new Rational(1, 1)), tex);
//        
//        sh = decoder.firstStream(JavConstants.AVMEDIA_TYPE_AUDIO);
//        //AudioLinePlayer player = new AudioLinePlayer(sh.audioFormat(), playCont.masterClock());
//        //playCont.caster().addListener(player);
//        //driver.openAudioStream(sh, sh.audioFormat(), player);
//        
//        GLCapabilities glc = new GLCapabilities();
//        GLCanvas canvas    = new GLCanvas(glc);
//        
//        JFrame frame = new JFrame();
//        frame.setSize(1024, 768);
//        frame.setLocationRelativeTo(null);
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.getContentPane().add(canvas);
//        
//        canvas.addGLEventListener(new GLEventListener() {
//
//            public void display(GLAutoDrawable gld) {
//                GL gl = gld.getGL();
//                
//                playCont.updateClocks();
//                driver.pushDraw(gl);
//                driver.popDraw(gl);
//                
//                gl.glMatrixMode(GL_PROJECTION);
//                gl.glLoadIdentity();
//                gl.glMatrixMode(GL_MODELVIEW);
//                gl.glLoadIdentity();
//                
//                gl.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
//                
//                gl.glClearColor(0, 0, 0, 0);
//                gl.glClear(GL_COLOR_BUFFER_BIT);
//                
//                gl.glColor4d(1,1,1,1);
//                tex.pushDraw(gl);
//                gl.glBegin(GL_QUADS);
//                
//                gl.glTexCoord2d(0, 1);
//                gl.glVertex2d(0, 0);
//                
//                gl.glTexCoord2d(1, 1);
//                gl.glVertex2d(1, 0);
//                
//                gl.glTexCoord2d(1, 0);
//                gl.glVertex2d(1, 1);
//                
//                gl.glTexCoord2d(0, 0);
//                gl.glVertex2d(0, 1);
//                
//                gl.glEnd();
//                tex.popDraw(gl);
//            }
//            
//            public void displayChanged(GLAutoDrawable arg0, boolean arg1, boolean arg2) {}
//
//            public void init(GLAutoDrawable arg0) {}
//
//            public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {}
//        
//        });        
//        
//        
//        frame.setVisible(true);
//        new Animator(canvas).start();
//        
//        
//        try {
//            Thread.sleep(1000L);
//            System.out.println("SEEK");
//            playCont.control().seek(5000000L);
//            System.out.println("PLAY");
//            playCont.control().playStart();
//            
//            while(true) {
//                Thread.sleep(5000L);
//                System.out.println("SEEK");
//                playCont.control().seek(10000000L);
//            }
//            
//            
//        }catch(Exception ex) {}
//    }
//
    

}
