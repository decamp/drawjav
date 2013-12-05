package bits.drawjav.video;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Random;

import bits.jav.Jav;


/**
 * @author decamp
 */
public class TestMp4Writer {

    public static void main( String[] args ) throws IOException {
        test1();
    }

    
    static void test1() throws IOException {
        Jav.init();
        
        File file = new File( "/tmp/thing2.mp4" );
        if( file.exists() ) {
            file.delete();
        }
        
        final int w = 640;
        final int h = 480;
        
        Random rand = new Random();
        ByteBuffer buf = ByteBuffer.allocateDirect( w * h * 3 );
                
        Mp4Writer out = new Mp4Writer();
        out.size( w, h );
        out.bitrate( 400000000 );
        out.open( file );
        
        for( int i = 0; i < 255; i++ ) {
            byte v = (byte)i;
            buf.clear();
            
            for( int j = 0; j < w*h; j++ ) {
                byte r = (byte)rand.nextInt();
                buf.put( (byte)0xFF );
                buf.put( v );
                buf.put( r );
            }
            buf.flip();
            
            out.write( buf, w * 3 );
        }
        
        out.close();
    }
    

    
}
