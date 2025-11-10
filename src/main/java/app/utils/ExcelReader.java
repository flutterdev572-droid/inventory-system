package app.utils;

import app.services.ItemImportDTO;
import app.services.ItemImportDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class ExcelReader {

    public static List<ItemImportDTO> readItemsFromExcel(File file) throws Exception {
        List<ItemImportDTO> items = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // أول sheet

            // تخطي الصف الأول (العناوين)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                ItemImportDTO item = new ItemImportDTO();

                // كود الصنف (العمود 0)
                Cell codeCell = row.getCell(0);
                if (codeCell != null) {
                    item.setItemCode(getCellValueAsString(codeCell));
                }

                // اسم الصنف (العمود 1)
                Cell nameCell = row.getCell(1);
                if (nameCell != null) {
                    item.setItemName(getCellValueAsString(nameCell));
                }

                // اسم الوحدة (العمود 2)
                Cell unitCell = row.getCell(2);
                if (unitCell != null) {
                    item.setUnitName(getCellValueAsString(unitCell));
                }

                // الحد الأدنى (العمود 3)
                Cell minQtyCell = row.getCell(3);
                if (minQtyCell != null) {
                    item.setMinQuantity(getCellValueAsDouble(minQtyCell));
                }

                // الكمية الأولية (العمود 4)
                Cell initialQtyCell = row.getCell(4);
                if (initialQtyCell != null) {
                    item.setInitialQuantity(getCellValueAsDouble(initialQtyCell));
                }

                // السعر (العمود 5)
                Cell priceCell = row.getCell(5);
                if (priceCell != null) {
                    item.setPrice(getCellValueAsDouble(priceCell));
                }

                // نضيف الصنف فقط إذا كان يحتوي على البيانات الأساسية
                if (item.getItemCode() != null && !item.getItemCode().isEmpty() &&
                        item.getItemName() != null && !item.getItemName().isEmpty() &&
                        item.getUnitName() != null && !item.getUnitName().isEmpty()) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;

        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            case BOOLEAN:
                return cell.getBooleanCellValue() ? 1.0 : 0.0;
            default:
                return 0.0;
        }
    }
}