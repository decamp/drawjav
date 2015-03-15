package bits.drawjav.video;

import bits.draw3d.Texture;
import bits.drawjav.pipe.*;
import com.google.common.eventbus.EventBus;


/**
 * @author Philip DeCamp
 */
public class VideoTextureUnit implements AvUnit {


    private final VideoTexture mTex = new VideoTexture();


    public VideoTextureUnit() {}



    public Texture texture() {
        return mTex;
    }


    @Override
    public void open( EventBus bus ) {}

    @Override
    public int inputNum() {
        return 1;
    }

    @Override
    public InPad input( int idx ) {
        return mTex;
    }

    @Override
    public int outputNum() {
        return 0;
    }

    @Override
    public OutPad output( int idx ) {
        return null;
    }


    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void clear() {
        mTex.clear();
    }

}
