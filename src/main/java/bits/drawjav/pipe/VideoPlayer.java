package bits.drawjav.pipe;

import bits.draw3d.Texture;
import bits.drawjav.*;
import bits.drawjav.video.VideoTextureUnit;
import bits.jav.Jav;
import bits.jav.util.Rational;
import bits.microtime.PlayClock;
import bits.microtime.PlayController;

import java.io.IOException;


/**
 * @author Philip DeCamp
 */
public class VideoPlayer {

    private final MemoryManager mMem;
    private final PlayClock     mClock;

    private final PacketReaderUnit   mReader;
    private final VideoResamplerUnit mResampler;
    private final VideoTextureUnit   mTexture;
    private final GraphDriver mDriver;


    public VideoPlayer( MemoryManager optMem,
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

        StreamFormat dstFormat = StreamFormat.createVideo( -1, -1, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        Stream dstStream = new BasicStream( dstFormat );

        mReader    = new PacketReaderUnit( reader );
        mResampler = new VideoResamplerUnit( mMem );
        mTexture   = new VideoTextureUnit();

        AvGraph graph = new AvGraph();
        graph.connect( mReader, mReader.output( 0 ), mResampler, mResampler.input( 0 ), null );
        graph.connect( mResampler, mResampler.output( 0 ), mTexture, mTexture.input( 0 ), dstFormat );

        mDriver = new GraphDriver( graph, graph, mClock );
    }


    public void start() {
        mDriver.start();
    }


    public PlayClock clock() {
        return mClock;
    }


    public Texture texture() {
        return mTexture.texture();
    }

}
