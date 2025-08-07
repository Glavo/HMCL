package org.jackhuang.hmcl.ui.image.jxl.entropy;

import java.io.IOException;

import org.jackhuang.hmcl.ui.image.jxl.io.Bitreader;

public abstract class SymbolDistribution {
    protected HybridIntegerConfig config;
    protected int logBucketSize;
    protected int alphabetSize;
    protected int logAlphabetSize;

    public abstract int readSymbol(Bitreader reader, EntropyState state) throws IOException;
}
