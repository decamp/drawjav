package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.jav.Jav;
import bits.microtime.PlayController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Philip DeCamp
 */
public class AudioPlayer {

    private final MemoryManager mMem;
    private final Filter        mReader;
    private final Filter        mResampler;
    private final LineOutFilter mLineOut;


    public AudioPlayer( MemoryManager optMem,
                        PacketReader reader )
                        throws IOException
    {
        if( optMem == null ) {
            optMem = new PoolMemoryManager( 128, 64 * 1024 * 1024, -1, -1 );
        }
        mMem = optMem;
        mReader = new ReaderFilter( reader );

        PlayController playCont = PlayController.createAuto();

        AudioFormat format = new AudioFormat( 1, 48000, Jav.AV_SAMPLE_FMT_FLT );
        mResampler = new ResamplerFilter( format, optMem.audioAllocator( null, format ) );
        mLineOut = new LineOutFilter( format, playCont );

        playCont.control().clockStart();
        new PlayerThread().start();
    }


    private class PlayerThread extends Thread {
        PlayerThread() {
            super( "AudioPlayer" );
        }

        public void run() {
            SourcePad source0 = mReader.sourcePad( 0 );
            SinkPad   sink1   = mResampler.sinkPad( 0 );
            SourcePad source1 = mResampler.sourcePad( 0 );
            SinkPad   sink2   = mLineOut.sinkPad( 0 );

            AudioPacket packet = null;
            AudioPacket[] arr = { null };
            FilterErr err;

            try {
                while( true ) {
                    err = source0.remove( arr, 0 );
                    switch( err ) {
                    case DONE:
                        AudioPacket ap = arr[0];
                        System.out.println( "DONE: " + ap.startMicros() );
                        break;
                    case NONE:
                        System.out.println( "Nothing" );
                        continue;
                    case TIMEOUT:
                        System.out.println( "Timeout" );
                        continue;
                    case NO_INPUT:
                        System.out.println( "EOD" );
                        return;
                    }

                    err = sink1.offer( arr[0], 0 );
                    switch( err ) {
                    case DONE:
                        break;
                    case NONE:
                        System.out.println( "1 Nothing" );
                        continue;
                    case TIMEOUT:
                        System.out.println( "1 Timeout" );
                        continue;
                    case NO_INPUT:
                        System.out.println( "1 EOD" );
                        return;
                    }

                    arr[0].deref();
                    err = source1.remove( arr, 0 );
                    if( err != FilterErr.DONE ) {
                        System.err.println( err );
                        return;
                    }

                    while( true ) {
                        err = sink2.offer( arr[0], 0 );
                        if( err == FilterErr.DONE ) {
                            break;
                        } else if( err == FilterErr.NO_INPUT ) {
                            System.err.println( "1 EOD" );
                            return;
                        }
                    }

                    arr[0].deref();
                }
            } catch( Exception ex ) {
                ex.printStackTrace();
            }
        }

    }


}
