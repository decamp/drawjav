package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.IOException;
import java.nio.channels.Channel;


/**
 * @author Philip DeCamp
 */
public class AudioPlayer implements Channel {

    private final MemoryManager mMem;
    private final PlayClock     mClock;

    private final PacketReaderUnit   mReader;
    private final AudioResamplerUnit mResampler;
    private final AudioPacketClipper mClipper;
    private final SolaUnit           mSola;
    private final LineOutUnit        mLineOut;
    private final GraphDriver        mDriver;


    public AudioPlayer( MemoryManager optMem,
                        PlayClock optClock,
                        PacketReader reader )
                        throws IOException
    {
        if( optMem == null ) {
            optMem = new PoolPerFormatMemoryManager( 128, 64 * 1024 * 1024, -1, -1 );
        }
        mMem = optMem;
        if( optClock == null ) {
            optClock = PlayController.createAuto().clock();
        }
        mClock = optClock;

        StreamFormat srcFormat = StreamFormat.createAudio( 1, 48000, Jav.AV_SAMPLE_FMT_S16P );
        StreamFormat dstFormat = StreamFormat.createAudio( 1, 48000, Jav.AV_SAMPLE_FMT_FLT );

        mReader    = new PacketReaderUnit( reader );
        mClipper   = new AudioPacketClipper( optMem ); //optMem.audioAllocator( srcStream ) );
        mResampler = new AudioResamplerUnit( optMem );
        mSola      = new SolaUnit( optMem );
        mLineOut   = new LineOutUnit( null );

        AvGraph graph = new AvGraph();
        graph.connect( mReader,    mReader.output( 0 ),    mClipper,   mClipper.input( 0 ),   srcFormat );
        graph.connect( mClipper,   mClipper.output( 0 ),   mResampler, mResampler.input( 0 ), srcFormat );
        graph.connect( mResampler, mResampler.output( 0 ), mSola,      mSola.input( 0 ),      dstFormat );
        graph.connect( mSola,      mSola.output( 0 ),      mLineOut,   mLineOut.input( 0 ),   dstFormat );

        mDriver = GraphDriver.createThreaded( mClock, graph );
    }


    public PlayClock clock() {
        return mClock;
    }


    public void start() {
        mDriver.start();
    }

    @Override
    public boolean isOpen() {
        return mDriver.isOpen();
    }

    @Override
    public void close() throws IOException {
        mDriver.close();
    }

}
