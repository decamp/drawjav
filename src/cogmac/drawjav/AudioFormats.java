package cogmac.drawjav;

import bits.jav.JavConstants;


/**
 * @author decamp
 */
public final class AudioFormats {

    
    public static boolean isCompatible( AudioFormat source, AudioFormat dest ) {
        if( source == null || dest == null ) {
            return true;
        }
        if( source.channels() > 0 && 
            dest.channels() > 0 && 
            source.channels() != dest.channels() ) 
        {
            return false;
        }
        
        if( source.sampleFormat() != JavConstants.AV_SAMPLE_FMT_NONE &&
            dest.sampleFormat() != JavConstants.AV_SAMPLE_FMT_NONE &&
            source.sampleFormat() != dest.sampleFormat() )
        {
            return false;
        }
        
        if( source.sampleRate() > 0 && 
            dest.sampleRate() > 0 && 
            source.sampleRate() != dest.sampleRate() ) 
        {
            return false;
        }
        
        return true;
    }
    
        
    public static boolean isFullyDefined( AudioFormat format ) {
        if( format.channels() < 0 || format.sampleRate() < 0 ) {
            return false;
        }
        return format.sampleFormat() != JavConstants.AV_SAMPLE_FMT_NONE;
    }
    
    
    public static AudioFormat merge( AudioFormat source, AudioFormat dest ) {
        if( source == null ) {
            return dest == null ? new AudioFormat() : dest;
        }
        if( dest == null ) {
            return source;
        }
        return new AudioFormat( dest.channels() >= 0 ? dest.channels() : source.channels(),
                                dest.sampleRate() >= 0 ? dest.sampleRate() : source.sampleRate(),
                                dest.sampleFormat() != JavConstants.AV_SAMPLE_FMT_NONE ? dest.sampleFormat() : source.sampleFormat() );
        
    }
    


    private AudioFormats() {}
    
}
