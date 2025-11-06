package app.utils;

import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

public class RawThermalPrinter {

    public static void printReceiptAsImage(String itemName, String unit, double quantity,
                                           String receiver, String employee, String notes) {
        try {
            System.out.println("🟡 بدء إنشاء إيصال كصورة...");

            // 🔍 البحث عن الطابعة المناسبة
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService selectedPrinter = null;
            for (PrintService s : services) {
                String name = s.getName().toLowerCase();
                if (name.contains("xprinter") || name.contains("xp") || name.contains("pos")) {
                    selectedPrinter = s;
                    break;
                }
            }

            if (selectedPrinter == null) {
                System.out.println("❌ لم يتم العثور على طابعة حرارية!");
                return;
            }
            System.out.println("✅ تم العثور على الطابعة: " + selectedPrinter.getName());

            // ⚙️ حساب الطول ديناميكيًا حسب عدد الأسطر
            int baseHeight = 400; // مساحة للهيدر والفوتر
            int lines = 6; // عدد السطور الأساسية
            if (notes != null && !notes.isEmpty()) lines++;
            int height = baseHeight + (lines * 45); // 45 بيكـسل لكل سطر تقريبي

            int width = 384; // عرض طابعة 58mm

            // 🖼️ إنشاء الصورة
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            Graphics2D g = img.createGraphics();

            // خلفية بيضاء
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // إعداد الخط
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial Unicode MS", Font.PLAIN, 22));

            int y = 20;

            // 🖼️ تحميل اللوجو من الموارد
            try {
                InputStream logoStream = RawThermalPrinter.class.getResourceAsStream("/images/colord_logo.png");
                if (logoStream != null) {
                    BufferedImage logo = ImageIO.read(logoStream);

                    // تصغير اللوجو حسب عرض الطابعة
                    int logoWidth = 200;
                    int logoHeight = (int) ((double) logo.getHeight() / logo.getWidth() * logoWidth);

                    Image scaledLogo = logo.getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH);
                    int x = (width - logoWidth) / 2;
                    g.drawImage(scaledLogo, x, y, null);

                    y += logoHeight + 20;
                } else {
                    System.out.println("⚠️ لم يتم العثور على ملف اللوجو!");
                    y += 40;
                }
            } catch (Exception ex) {
                System.out.println("⚠️ فشل تحميل اللوجو:");
                ex.printStackTrace();
                y += 40;
            }

            // 🏷️ عنوان الشركة
            g.drawString("CHEM TECH", 120, y);
            y += 40;
            g.drawLine(0, y, width, y);
            y += 40;

            // 🔹 محتوى الإيصال
            g.drawString("الصنف: " + itemName, 10, y); y += 35;
            g.drawString("الوحدة: " + unit, 10, y); y += 35;
            g.drawString("الكمية: " + quantity, 10, y); y += 35;
            g.drawString("المستلم: " + receiver, 10, y); y += 35;
            g.drawString("الموظف: " + employee, 10, y); y += 35;

            if (notes != null && !notes.isEmpty()) {
                g.drawString("ملاحظات: " + notes, 10, y);
                y += 35;
            }

            g.drawLine(0, y, width, y);
            y += 40;

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            g.drawString("التاريخ: " + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")), 10, y);
            y += 50;

            // ⚙️ الفوتر
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("تحت إدارة كيم تك", 100, y); y += 30;
            g.drawString("تم بواسطة عبدالله أيمن", 70, y); y += 50;

            // ✂️ مسافة قبل القص
            g.drawString(" ", 10, y + 60);
            g.drawString(" ", 10, y + 80);

            g.dispose();

            // 🧩 تحويل الصورة إلى PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] imageData = baos.toByteArray();

            // 🖨️ إرسال الصورة للطابعة
            DocFlavor flavor = DocFlavor.INPUT_STREAM.PNG;
            DocPrintJob job = selectedPrinter.createPrintJob();
            Doc doc = new SimpleDoc(new ByteArrayInputStream(imageData), flavor, null);

            PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
            attr.add(new Copies(1));
            attr.add(OrientationRequested.PORTRAIT);
            attr.add(new MediaPrintableArea(0, 0, 58, height / 8f, MediaPrintableArea.MM));

            job.print(doc, attr);

            System.out.println("✅ تم إرسال الإيصال للطابعة بنجاح.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
