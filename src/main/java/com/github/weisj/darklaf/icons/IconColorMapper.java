/*
 * MIT License
 *
 * Copyright (c) 2019 Jannis Weis
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.weisj.darklaf.icons;

import com.kitfox.svg.Defs;
import com.kitfox.svg.LinearGradient;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGElementException;
import com.kitfox.svg.animation.AnimationElement;
import com.kitfox.svg.app.beans.SVGIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jannis Weis
 */
public final class IconColorMapper {
    private static final Logger LOGGER = Logger.getLogger(IconLoader.class.getName());

    public static void patchColors(@NotNull final SVGIcon svgIcon) {
        var universe = svgIcon.getSvgUniverse();
        SVGDiagram diagram = universe.getDiagram(svgIcon.getSvgURI());
        try {
            loadColors(diagram);
        } catch (SVGElementException e) {
            LOGGER.log(Level.SEVERE, "Failed patching colors. " + e.getMessage(), e.getStackTrace());
        }
    }

    private static void loadColors(@NotNull final SVGDiagram diagram) throws SVGElementException {
        var root = diagram.getRoot();
        var defs = diagram.getElement("colors");
        if (defs == null) return;
        var children = defs.getChildren(null);
        root.removeChild(defs);

        var themedDefs = new Defs();
        themedDefs.addAttribute("id", AnimationElement.AT_XML, "colors");
        root.loaderAddChild(null, themedDefs);

        for (var child : children) {
            if (child instanceof LinearGradient) {
                var id = ((LinearGradient) child).getId();
                var c = UIManager.getColor(id);
                if (c == null) {
                    c = Color.RED;
                    LOGGER.warning("Could not load color with id'" + id + "'. Using Color.RED instead.");
                }
                themedDefs.loaderAddChild(null, createColor(c, id));
            }
        }
    }

    @NotNull
    private static LinearGradient createColor(final Color c, final String name) throws SVGElementException {
        var grad = new LinearGradient();
        grad.addAttribute("id", AnimationElement.AT_XML, name);
        grad.setStops(new Color[]{c, c}, new float[]{0.0f, 1.0f});
        return grad;
    }
}