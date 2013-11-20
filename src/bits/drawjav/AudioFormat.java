package bits.drawjav;

import static bits.jav.Jav.*;
import bits.jav.codec.JavCodecContext;


/**
 * @author decamp
 */
public class AudioFormat {


    public static AudioFormat fromCodecContext( JavCodecContext cc ) {
        return new AudioFormat( cc.channels(), cc.sampleRate(), cc.sampleFormat() );
    }



    private final int mChannels;
    private final int mSampleRate;
    private final int mSampleFormat;


    public AudioFormat() {
        mChannels = -1;
        mSampleRate = -1;
        mSampleFormat = AV_SAMPLE_FMT_NONE;
    }


    public AudioFormat( int channels, int sampleRate, int sampleFormat ) {
        mChannels = channels >= 0 ? channels : -1;
        mSampleRate = sampleRate >= 0 ? sampleRate : -1;
        mSampleFormat = sampleFormat;
    }



    /**
     * @return number of channels, or -1 if undefined.
     */
    public int channels() {
        return mChannels;
    }

    /**
     * @return sample rate in Hertz, or -1 if undefined.
     */
    public int sampleRate() {
        return mSampleRate;
    }

    /**
     * @return sample format as found in JavConstants.AV_SAMPLE_FMT_NONE to
     *         JavConstants.AV_SAMPLE_FMT_NB.
     */
    public int sampleFormat() {
        return mSampleFormat;
    }



    @Override
    public boolean equals( Object o ) {
        if( !(o instanceof AudioFormat) )
            return false;

        AudioFormat f = (AudioFormat)o;

        return mChannels == f.mChannels &&
               mSampleRate == f.mSampleRate &&
               mSampleFormat == f.mSampleFormat;
    }


    @Override
    public int hashCode() {
        return mChannels ^ mSampleRate ^ mSampleFormat;
    }


    @Override
    public String toString() {
        StringBuilder s = new StringBuilder( "AudioFormat [ chans: " );
        s.append( mChannels );
        s.append( " rate: " );
        s.append( mSampleRate );
        s.append( " sampleFormat: " );
        s.append( mSampleFormat );
        s.append( "]" );
        return s.toString();
    }

}
