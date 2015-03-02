package bits.drawjav.audio;

import java.io.File;


/**
 * @author Philip DeCamp
 */
public class TestWavWriter {

    public static void main( String[] args ) throws Exception {
        test1();
    }

    static void test1() throws Exception {
        int sampleRate = 44100;
        float[] samps = genSineWave( sampleRate, sampleRate * 5, 100 );
        WavWriter wav = new WavWriter( new File( "/tmp/thing.wav" ), 1, sampleRate );
        wav.writeFloats( samps, 0, samps.length );
        wav.close();

//        WavWriter.write( new float[][]{ samps }, 44100, new File( "/tmp/thing2.wav" ) );
        System.out.println( "Complete." );
    }



    static float[] genSineWave( int sampleRate, int sampleNum, double hertz ) {
        float[] ret = new float[sampleNum];
        double angDelta = 2.0 * Math.PI * hertz / sampleRate;

        for( int i = 0; i < sampleNum; i++ ) {
            ret[i] = (float)Math.cos( i * angDelta );
        }

        return ret;
    }

}


