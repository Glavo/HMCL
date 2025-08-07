package org.jackhuang.hmcl.ui.image.jxl.frame.group;

import java.io.IOException;

import org.jackhuang.hmcl.ui.image.jxl.frame.Frame;
import org.jackhuang.hmcl.ui.image.jxl.frame.FrameFlags;
import org.jackhuang.hmcl.ui.image.jxl.frame.modular.ModularChannel;
import org.jackhuang.hmcl.ui.image.jxl.frame.modular.ModularStream;
import org.jackhuang.hmcl.ui.image.jxl.frame.vardct.HFMetadata;
import org.jackhuang.hmcl.ui.image.jxl.frame.vardct.LFCoefficients;
import org.jackhuang.hmcl.ui.image.jxl.io.Bitreader;
import org.jackhuang.hmcl.ui.image.jxl.util.Dimension;
import org.jackhuang.hmcl.ui.image.jxl.util.ImageBuffer;

public class LFGroup {

    public final LFCoefficients lfCoeff;
    public final HFMetadata hfMetadata;
    public final int lfGroupID;
    public final Frame frame;
    public final Dimension size;
    public final ModularStream modularLFGroup;

    public LFGroup(Bitreader reader, Frame parent, int index, ModularChannel[] replaced,
            ImageBuffer[] lfBuffer) throws IOException {
        this.lfGroupID = index;
        this.frame = parent;
        Dimension pixelSize = frame.getLFGroupSize(lfGroupID);
        size = new Dimension(pixelSize.height >> 3, pixelSize.width >> 3);
        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT)
            this.lfCoeff = new LFCoefficients(reader, this, parent, lfBuffer);
        else
            this.lfCoeff = null;

        modularLFGroup = new ModularStream(reader, frame, 1 + frame.getNumLFGroups() +
            lfGroupID, replaced);
        modularLFGroup.decodeChannels(reader);

        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT)
            this.hfMetadata = new HFMetadata(reader, this, parent);
        else
            this.hfMetadata = null;       
    }
}
