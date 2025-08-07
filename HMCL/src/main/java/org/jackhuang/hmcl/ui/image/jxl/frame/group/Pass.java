package org.jackhuang.hmcl.ui.image.jxl.frame.group;

import java.io.IOException;

import org.jackhuang.hmcl.ui.image.jxl.bundle.PassesInfo;
import org.jackhuang.hmcl.ui.image.jxl.frame.Frame;
import org.jackhuang.hmcl.ui.image.jxl.frame.FrameFlags;
import org.jackhuang.hmcl.ui.image.jxl.frame.modular.ModularChannel;
import org.jackhuang.hmcl.ui.image.jxl.frame.modular.ModularStream;
import org.jackhuang.hmcl.ui.image.jxl.frame.vardct.HFPass;
import org.jackhuang.hmcl.ui.image.jxl.io.Bitreader;
import org.jackhuang.hmcl.ui.image.jxl.util.MathHelper;

public class Pass {

    public final int minShift;
    public final int maxShift;
    public final ModularChannel[] replacedChannels;
    public final HFPass hfPass;

    public Pass(Bitreader reader, Frame frame, int passIndex, int prevMinshift) throws IOException {
        maxShift = passIndex > 0 ? prevMinshift : 3;
        int n = -1;
        PassesInfo passes = frame.getFrameHeader().passes;
        for (int i = 0; i < passes.lastPass.length; i++) {
            if (passes.lastPass[i] == passIndex) {
                n = i;
                break;
            }
        }
        minShift = n >= 0 ? MathHelper.ceilLog1p(passes.downSample[n] - 1) : maxShift;
        ModularStream stream = frame.getLFGlobal().globalModular;
        replacedChannels = new ModularChannel[stream.getEncodedChannelCount()];
        for (int i = 0; i < replacedChannels.length; i++) {
            ModularChannel chan = stream.getChannel(i);
            if (!chan.isDecoded()) {
                int m = Math.min(chan.vshift, chan.hshift);
                if (minShift <= m && m < maxShift)
                    replacedChannels[i] = new ModularChannel(chan);
            }
        }

        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT)
            hfPass = new HFPass(reader, frame, passIndex);
        else
            hfPass = null;
    }
}
