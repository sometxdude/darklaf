/*
 * MIT License
 *
 * Copyright (c) 2020 Jannis Weis
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
package com.github.weisj.darklaf.ui.text;

import com.github.weisj.darklaf.util.DarkSwingUtil;
import com.github.weisj.darklaf.util.DarkUIUtil;
import com.github.weisj.darklaf.util.GraphicsContext;
import com.github.weisj.darklaf.util.GraphicsUtil;
import sun.awt.SunToolkit;
import sun.swing.DefaultLookup;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.ComponentInputMapUIResource;
import javax.swing.plaf.InputMapUIResource;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.Caret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.EditorKit;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.TextAction;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Jannis Weis
 */
public abstract class DarkTextUI extends BasicTextUI implements PropertyChangeListener {

    protected JTextComponent editor;

    @Override
    protected Caret createCaret() {
        return new DarkCaret(getDefaultCaretStyle());
    }

    protected abstract DarkCaret.CaretStyle getDefaultCaretStyle();

    @Override
    protected void installDefaults() {
        super.installDefaults();
        editor.putClientProperty("JTextComponent.roundedSelection",
                                 UIManager.getBoolean("TextComponent.roundedSelection"));
    }

    @Override
    protected void uninstallDefaults() {
        super.uninstallDefaults();
        editor.putClientProperty("JTextComponent.roundedSelection", null);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        String key = evt.getPropertyName();
        if ("JTextComponent.roundedSelection".equals(key)) {
            editor.repaint();
        }
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        editor.addPropertyChangeListener(this);
    }

    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        editor.removePropertyChangeListener(this);
    }

    @Override
    protected void paintBackground(final Graphics g) {
        Container parent = editor.getParent();
        if (editor.isOpaque() && parent != null) {
            g.setColor(parent.getBackground());
            g.fillRect(0, 0, editor.getWidth(), editor.getHeight());
        }

        Border border = editor.getBorder();
        if (border instanceof DarkTextBorder) {
            paintBorderBackground((Graphics2D) g, editor);
        } else if (border != null) {
            Insets ins = border.getBorderInsets(editor);
            if (ins != null) {
                g.setColor(editor.getBackground());
                g.fillRect(ins.left, ins.top, editor.getWidth() - ins.left - ins.right,
                           editor.getHeight() - ins.top - ins.bottom);
            }
        }
    }

    @Override
    protected void paintSafely(final Graphics g) {
        GraphicsContext config = GraphicsUtil.setupAntialiasing(g);
        super.paintSafely(g);
        config.restore();
    }

    protected void paintBorderBackground(final Graphics2D g, final JTextComponent c) {
        g.setColor(c.getBackground());
        Rectangle r = getDrawingRect(c);
        int arc = getArcSize(c);
        DarkUIUtil.fillRoundRect(g, r.x, r.y, r.width, r.height, arc);
    }

    public Rectangle getDrawingRect(final JTextComponent c) {
        Border border = c.getBorder();
        int w = 0;
        if (border instanceof DarkTextBorder) {
            w = ((DarkTextBorder) border).getBorderSize();
        }
        return new Rectangle(w, w, c.getWidth() - 2 * w, c.getHeight() - 2 * w);
    }

    protected int getArcSize(final JComponent c) {
        Border border = c.getBorder();
        if (border instanceof DarkTextBorder) {
            return ((DarkTextBorder) border).getArcSize(c);
        }
        return 0;
    }

    protected void installKeyboardActions() {
        // backward compatibility support... keymaps for the UI
        // are now installed in the more friendly input map.
        editor.setKeymap(createKeymap());

        InputMap km = getInputMap();
        if (km != null) {
            SwingUtilities.replaceUIInputMap(editor, JComponent.WHEN_FOCUSED,
                                             km);
        }

        ActionMap map = getActionMap();
        if (map != null) {
            SwingUtilities.replaceUIActionMap(editor, map);
        }

        updateFocusAcceleratorBinding(false);
    }

    @Override
    public void installUI(final JComponent c) {
        if (c instanceof JTextComponent) {
            editor = (JTextComponent) c;
        }
        super.installUI(c);
    }

    @Override
    protected Highlighter createHighlighter() {
        return new DarkHighlighter();
    }

    /*
     * Implementation of BasicTextUI.
     */

    /**
     * Get the InputMap to use for the UI.
     *
     * @return the input map
     */
    protected InputMap getInputMap() {
        InputMap map = new InputMapUIResource();

        InputMap shared =
                (InputMap) DefaultLookup.get(editor, this,
                                             getPropertyPrefix() + ".focusInputMap");
        if (shared != null) {
            map.setParent(shared);
        }
        return map;
    }

    protected ActionMap getActionMap() {
        String mapName = getPropertyPrefix() + ".actionMap";
        ActionMap map = (ActionMap) UIManager.get(mapName);

        if (map == null) {
            map = createActionMap();
            if (map != null) {
                UIManager.getLookAndFeelDefaults().put(mapName, map);
            }
        }
        ActionMap componentMap = new ActionMapUIResource();
        componentMap.put("requestFocus", new FocusAction());
        /*
         * fix for bug 4515750
         * JTextField & non-editable JTextArea bind return key - default btn not accessible
         *
         * Wrap the return action so that it is only enabled when the
         * component is editable. This allows the default button to be
         * processed when the text component has focus and isn't editable.
         *
         */
        if (getEditorKit(editor) instanceof DefaultEditorKit) {
            if (map != null) {
                Object obj = map.get(DefaultEditorKit.insertBreakAction);
                if (obj != null
                        && obj instanceof DefaultEditorKit.InsertBreakAction) {
                    Action action = new TextActionWrapper((TextAction) obj);
                    componentMap.put(action.getValue(Action.NAME), action);
                }
            }
        }
        if (map != null) {
            componentMap.setParent(map);
        }
        return componentMap;
    }

    /**
     * Invoked when the focus accelerator changes, this will update the key bindings as necessary.
     *
     * @param changed the changed
     */
    @SuppressWarnings("MagicConstant")
    protected void updateFocusAcceleratorBinding(final boolean changed) {
        char accelerator = editor.getFocusAccelerator();

        if (changed || accelerator != '\0') {
            InputMap km = SwingUtilities.getUIInputMap
                    (editor, JComponent.WHEN_IN_FOCUSED_WINDOW);

            if (km == null && accelerator != '\0') {
                km = new ComponentInputMapUIResource(editor);
                SwingUtilities.replaceUIInputMap(editor, JComponent.
                        WHEN_IN_FOCUSED_WINDOW, km);
                ActionMap am = getActionMap();
                SwingUtilities.replaceUIActionMap(editor, am);
            }
            if (km != null) {
                km.clear();
                if (accelerator != '\0') {
                    km.put(KeyStroke.getKeyStroke(accelerator, getFocusAcceleratorKeyMask()), "requestFocus");
                    km.put(KeyStroke.getKeyStroke(accelerator,
                                                  DarkSwingUtil.setAltGraphMask(getFocusAcceleratorKeyMask())),
                           "requestFocus");
                }
            }
        }
    }

    /**
     * Create a default action map.  This is basically the set of actions found exported by the component.
     *
     * @return the action map
     */
    public ActionMap createActionMap() {
        ActionMap map = new ActionMapUIResource();
        Action[] actions = editor.getActions();
        for (Action a : actions) {
            map.put(a.getValue(Action.NAME), a);
        }
        map.put(TransferHandler.getCutAction().getValue(Action.NAME),
                TransferHandler.getCutAction());
        map.put(TransferHandler.getCopyAction().getValue(Action.NAME),
                TransferHandler.getCopyAction());
        map.put(TransferHandler.getPasteAction().getValue(Action.NAME),
                TransferHandler.getPasteAction());
        return map;
    }

    protected static int getFocusAcceleratorKeyMask() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        if (tk instanceof SunToolkit) {
            return ((SunToolkit) tk).getFocusAcceleratorKeyMask();
        }
        return ActionEvent.ALT_MASK;
    }

    /**
     * Invoked when editable property is changed.
     * <p>
     * removing 'TAB' and 'SHIFT-TAB' from traversalKeysSet in case editor is editable adding 'TAB' and 'SHIFT-TAB' to
     * traversalKeysSet in case editor is non editable
     */
    @SuppressWarnings("deprecation")
    void updateFocusTraversalKeys() {
        /*
         * Fix for 4514331 Non-editable JTextArea and similar
         * should allow Tab to keyboard - accessibility
         */
        EditorKit editorKit = getEditorKit(editor);
        if (editorKit instanceof DefaultEditorKit) {
            Set<AWTKeyStroke> storedForwardTraversalKeys =
                    editor.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
            Set<AWTKeyStroke> storedBackwardTraversalKeys =
                    editor.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
            Set<AWTKeyStroke> forwardTraversalKeys =
                    new HashSet<>(storedForwardTraversalKeys);
            Set<AWTKeyStroke> backwardTraversalKeys =
                    new HashSet<>(storedBackwardTraversalKeys);
            if (editor.isEditable()) {
                forwardTraversalKeys.remove(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
                backwardTraversalKeys.remove(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_MASK));
            } else {
                forwardTraversalKeys.add(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
                backwardTraversalKeys.add(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_MASK));
            }
            LookAndFeel.installProperty(editor, "focusTraversalKeysForward", forwardTraversalKeys);
            LookAndFeel.installProperty(editor, "focusTraversalKeysBackward", backwardTraversalKeys);
        }
    }

    public class FocusAction extends AbstractAction {

        public void actionPerformed(final ActionEvent e) {
            editor.requestFocus();
        }

        public boolean isEnabled() {
            return editor.isEditable();
        }
    }

    /**
     * Wrapper for text actions to return isEnabled false in case editor is non editable
     */
    public class TextActionWrapper extends TextAction {
        TextAction action;

        public TextActionWrapper(final TextAction action) {
            super((String) action.getValue(Action.NAME));
            this.action = action;
        }

        /**
         * The operation to perform when this action is triggered.
         *
         * @param e the action event
         */
        public void actionPerformed(final ActionEvent e) {
            action.actionPerformed(e);
        }

        public boolean isEnabled() {
            return (editor == null || editor.isEditable()) && action.isEnabled();
        }
    }
}
