/*
 * Copyright (C) 2011 Lalit Pant <pant.lalit@gmail.com>
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 */
package net.kogics.kojo.action;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.kogics.kojo.core.KojoCtx;
import net.kogics.kojo.lite.CodeExecutionSupport;

public final class LoadFrom implements ActionListener {
	private KojoCtx ctx;
	private String ext = "kojo";

	public LoadFrom(KojoCtx ctx) {
		this.ctx = ctx;
	}
	
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Kojo Files", "kojo");
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        String loadDir = ctx.getLastLoadStoreDir();
        if (loadDir != null && loadDir != "") {
            File dir = new File(loadDir);
            if (dir.exists() && dir.isDirectory()) {
                chooser.setCurrentDirectory(dir);
            }
        }

        CodeExecutionSupport ces = (CodeExecutionSupport) CodeExecutionSupport.instance();
        try {
            ces.closeFileAndClrEditor();
            int returnVal = chooser.showOpenDialog(ctx.frame());

            if (returnVal == JFileChooser.APPROVE_OPTION) {
    			File selectedFile = chooser.getSelectedFile();
    			if (!selectedFile.getName().endsWith("." + ext)) {
    				selectedFile = new File(selectedFile.getAbsolutePath() + "."
    						+ ext);
    			}
                ctx.setLastLoadStoreDir(selectedFile.getParent());
                ces.openFileWithoutClose(selectedFile);
            }
        } catch (RuntimeException ex) {
            // ignore user cancel
        }
    }
}
