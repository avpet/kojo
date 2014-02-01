package xswing

import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import scala.collection.mutable.ArraySeq
import scala.reflect.ClassTag
import net.kogics.kojo.util.Read
import javax.swing.JSlider

//def borderWithMargin(m: Int) = {
//    import javax.swing.border._
//    import javax.swing.BorderFactory
//    val outsideBorder = BorderFactory.createLineBorder(color(128, 128, 128))
//    val insideBorder = new EmptyBorder(m, m, m, m)
//    new CompoundBorder(outsideBorder, insideBorder)
//}

trait PreferredMax { self: JComponent =>
  override def getMaximumSize = getPreferredSize
}

case class RowPanel(comps: JComponent*) extends JPanel with PreferredMax {
  comps.foreach { add(_) }
  setLayout(new FlowLayout)
  setBackground(Color.white)
}

case class ColPanel(comps: JComponent*) extends JPanel with PreferredMax {
  comps.foreach { add(_) }
  setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))
  setBackground(Color.white)
}

case class TextField(n: Int) extends JTextField(n) {
  def value = getText
}
case class Label(label: String) extends JLabel(label)
case class Button(label: String)(al: => Unit) extends JButton(label) {
  addActionListener(new ActionListener {
    def actionPerformed(e: ActionEvent) {
      al
    }
  })
}

case class DropDown[T](options: T *)(implicit reader: Read[T]) extends JComboBox(options.map(_.toString).toArray.asInstanceOf[Array[AnyRef]]) {
  setEditable(false)
  def value: T = reader.read(getSelectedItem.asInstanceOf[String])
}

case class Slider(min: Int, max: Int, curr: Int, spacing: Int) extends JSlider {
  setMinimum(min)
  setMaximum(max)
  setValue(curr)
  setMajorTickSpacing(spacing)
  setPaintTicks(true)
  setPaintLabels(true)
  setSnapToTicks(true)
  def value = getValue
}
