/**
 * This package contains classes for creating patches for text files output
 * in a GDIFF-like format.
 * <p>
 * The patch creation class is {@link com.nothome.delta.text.Delta}.
 * <p>
 * Example use:
 <pre>
 String source = ...;
 String target = ...;
 Delta d = new Delta();
 String patch = d.compute(source, target);
 
 TextPatcher p = new TextPatcher(source);
 String patchedSource = p.patch(patch);
 
 assert target.equals(patchedSource);
 </pre>
 *
 * @see com.nothome.delta.text.Delta
 * @see com.nothome.delta.GDiffPatcher
 */
package com.nothome.delta.text;