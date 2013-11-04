package cogmac.drawjav.audio;

import java.nio.ByteBuffer;

import bits.jav.JavConstants;


public enum AudioSampleFormat {
    
    UNSIGNED_BYTE {
        public int sampleSize() { 
            return 1; 
        }

        public void transfer(ByteBuffer in, float[] out, int len) {
            for(int i = 0; i < len; i++) {
                out[i] = ((in.get() & 0xFF) - 128) * BYTE_SCALE;
            }
        }
    },

    SIGNED_SHORT {
        public int sampleSize() { 
            return 2; 
        }

        public void transfer(ByteBuffer in, float[] out, int len) {
            for(int i = 0; i < len; i++) {
                out[i] = in.getShort() * SHORT_SCALE;
            }
        }

    },

    SIGNED_INT {
        public int sampleSize() {
            return 4;
        }

        public void transfer(ByteBuffer in, float[] out, int len) {
            for(int i = 0; i < len; i++) {
                out[i] = in.getInt() * INT_SCALE;
            }
        }
    },

    FLOAT {
        public int sampleSize() { 
            return 4; 
        }

        public void transfer(ByteBuffer in, float[] out, int len) {
            in.asFloatBuffer().get(out, 0, len);
        }
    },

    DOUBLE {
        public int sampleSize() { 
            return 8; 
        }

        public void transfer(ByteBuffer in, float[] out, int len) {
            for(int i = 0; i < len; i++) {
                out[i] = (float)in.getDouble();
            }
        }
    };
    
    
    private static final float BYTE_SCALE  = 1.0f / 128.0f;
    private static final float SHORT_SCALE = 1.0f / (Short.MAX_VALUE + 1f);
    private static final float INT_SCALE   = 1.0f / (Integer.MAX_VALUE + 1f);
        
    public abstract int sampleSize();
    public abstract void transfer(ByteBuffer in, float[] out, int len);

    
    public static AudioSampleFormat formatFromCode(int avSampleFormat) {
        switch(avSampleFormat) {
        case JavConstants.AV_SAMPLE_FMT_U8:
            return UNSIGNED_BYTE;
        
        case JavConstants.AV_SAMPLE_FMT_S16:
            return SIGNED_SHORT;
            
        case JavConstants.AV_SAMPLE_FMT_S32:
            return SIGNED_INT;
            
        case JavConstants.AV_SAMPLE_FMT_FLT:
            return FLOAT;
            
        case JavConstants.AV_SAMPLE_FMT_DBL:
            return DOUBLE;
            
        default:
            return null;
        }
    }
    
}
