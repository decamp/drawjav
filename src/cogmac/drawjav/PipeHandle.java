package cogmac.drawjav;

public interface PipeHandle {
    public StreamHandle srcHandle();
    public StreamHandle dstHandle();
    public Sink<Packet> sink();
}
