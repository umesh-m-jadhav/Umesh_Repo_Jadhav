package com.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class GeneratePlayerHtml {

    public static void main(String[] args) {
        boolean IsAuctionData = false; // Change as needed

        String baseFolder = "F:\\social\\AuctionCode\\CrikAuction\\";
        String defaultExcelPath = baseFolder + "DataFile\\Player_List.xlsx";
        String auctionFolder = "C:\\Users\\Admin\\Downloads";
        String outputPath = baseFolder + "RplPlayers.html";

        String excelPath;
        if (IsAuctionData) {
            excelPath = getLatestAuctionFile(auctionFolder);
            if (excelPath == null) {
                System.err.println("No AuctionResult*.xlsx file found in: " + auctionFolder);
                return;
            } else {
                System.out.println("Using latest auction file: " + excelPath);
            }
        } else {
            excelPath = defaultExcelPath;
            System.out.println("Using default player list: " + excelPath);
        }

        List<Player> players = readPlayersFromExcel(excelPath, IsAuctionData);

        // Read owner data from Owner sheet in both cases
        Map<String, OwnerData> ownerDataMap = readOwnerSheetsFromExcel(excelPath);

        generateHtml(players, ownerDataMap, outputPath, IsAuctionData);

        System.out.println("HTML generated successfully.");
        System.out.println("Source File: " + excelPath);
        System.out.println("Output File: " + outputPath);
        System.out.println("Total Players: " + players.size());
        System.out.println("Total Owners: " + ownerDataMap.size());
    }

    private static String getLatestAuctionFile(String folderPath) {
        try {
            List<Path> excelFiles = Files.list(Paths.get(folderPath))
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("AuctionResult") && name.endsWith(".xlsx");
                    })
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());

            if (!excelFiles.isEmpty()) {
                return excelFiles.get(0).toAbsolutePath().toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static List<Player> readPlayersFromExcel(String excelFilePath, boolean IsAuctionData) {
        List<Player> players = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();
            if (!iterator.hasNext()) return players;

            Row headerRow = iterator.next();
            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim();
                colMap.put(header, cell.getColumnIndex());
            }

            while (iterator.hasNext()) {
                Row row = iterator.next();
                Player p = new Player();

                p.name = getValue(formatter, row, colMap.get("Name"));
                p.towerFlat = getValue(formatter, row, colMap.get("TowerFlat"));
                p.mobile = getValue(formatter, row, colMap.get("Mobile"));
                p.unavailability = getValue(formatter, row, colMap.get("Unavailability"));
                p.photoURL = getValue(formatter, row, colMap.get("PhotoURL"));
                p.role = getValue(formatter, row, colMap.get("Role"));
                p.soldAt = getValue(formatter, row, colMap.get("FinalBid"));
                p.toTeam = getValue(formatter, row, colMap.get("SoldToTeam"));
                p.toOwner = getValue(formatter, row, colMap.get("TeamOwnerName"));
                p.ownerMobile = getValue(formatter, row, colMap.get("TeamOwnerMobile"));
                p.bidAmount = getValue(formatter, row, colMap.get("BidAmount"));
                if (!IsAuctionData) {
                    p.basePrice = getValue(formatter, row, colMap.get("BasePrice"));
                }

                if (p.name != null && !p.name.trim().isEmpty()) {
                    players.add(p);
                }
            }

        } catch (Exception e) {
            System.err.println("Error reading Excel: " + e.getMessage());
            e.printStackTrace();
        }

        players.sort(Comparator.comparing(p -> Optional.ofNullable(p.name).orElse("").toLowerCase()));
        return players;
    }

    private static Map<String, OwnerData> readOwnerSheetsFromExcel(String excelFilePath) {
        Map<String, OwnerData> ownerDataMap = new HashMap<>();
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet ownerSheet = workbook.getSheet("Owner");
            if (ownerSheet == null) {
                System.out.println("No 'Owner' sheet found in file.");
                return ownerDataMap;
            }

            Iterator<Row> iterator = ownerSheet.iterator();
            if (!iterator.hasNext()) return ownerDataMap;
            Row headerRow = iterator.next();

            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim();
                colMap.put(header, cell.getColumnIndex());
            }

            Integer nameCol = colMap.get("Name");
            Integer teamCol = colMap.get("TeamName");
            Integer photoCol = colMap.get("PhotoURL");
            Integer basePriceCol = colMap.get("BasePrice"); // new
            if (nameCol == null || teamCol == null) return ownerDataMap;

            while (iterator.hasNext()) {
                Row row = iterator.next();
                String ownerName = getValue(formatter, row, nameCol);
                String teamName = getValue(formatter, row, teamCol);
                String photoURL = getValue(formatter, row, photoCol);
                String basePrice = getValue(formatter, row, basePriceCol); // new

                if (ownerName != null && !ownerName.trim().isEmpty()) {
                    OwnerData od = new OwnerData();
                    od.teamName = teamName;
                    od.photoURL = (photoURL != null && !photoURL.trim().isEmpty()) ? photoURL : "Image_Not_Given.png";
                    od.basePrice = basePrice; // assign

                    Sheet teamSheet = workbook.getSheet(teamName);
                    if (teamSheet != null) {
                        List<Map<String, String>> rows = new ArrayList<>();
                        Iterator<Row> teamIterator = teamSheet.iterator();
                        if (teamIterator.hasNext()) {
                            Row teamHeader = teamIterator.next();
                            Map<Integer, String> headerMap = new HashMap<>();
                            for (Cell c : teamHeader) {
                                headerMap.put(c.getColumnIndex(), formatter.formatCellValue(c).trim());
                            }
                            while (teamIterator.hasNext()) {
                                Row r = teamIterator.next();
                                Map<String, String> rowData = new HashMap<>();
                                for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
                                    rowData.put(entry.getValue(), getValue(formatter, r, entry.getKey()));
                                }
                                rows.add(rowData);
                            }
                        }
                        od.sheetData = rows;
                    }

                    ownerDataMap.put(ownerName.trim(), od);
                }
            }

        } catch (Exception e) {
            System.err.println("Error reading Owner sheet: " + e.getMessage());
            e.printStackTrace();
        }

        return ownerDataMap;
    }

    private static String getValue(DataFormatter formatter, Row row, Integer colIndex) {
        if (colIndex == null || colIndex < 0) return "";
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        return formatter.formatCellValue(cell).trim();
    }

    private static void generateHtml(List<Player> players, Map<String, OwnerData> ownerDataMap, String outputPath, boolean IsAuctionData) {
        try (PrintWriter out = new PrintWriter(new FileWriter(outputPath))) {

            out.println("<!DOCTYPE html>");
            out.println("<html lang='en'>");
            out.println("<head>");
            out.println("  <meta charset='UTF-8'>");
            out.println("  <meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            out.println("  <title>RPL (R21 Premium League) Catalogue</title>");
            out.println("  <style>");
            out.println("    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 40px; background: #f5f7fa; color: #333; }");
            out.println("    .container { max-width: 820px; margin: auto; }");
            out.println("    .header { text-align: center; background: linear-gradient(135deg, #283c86, #45a247); color: white; padding: 28px; border-radius: 14px; box-shadow: 0 6px 20px rgba(0,0,0,0.12); }");
            out.println("    .header h2 { margin: 0; font-size: 28px; letter-spacing: 0.4px; }");
            out.println("    .header p { font-size: 15px; margin-top: 10px; color: #eef6ea; }");
            out.println("    select { width: 100%; padding: 12px; font-size: 16px; border-radius: 10px; border: 1px solid #cfd8dc; margin-top: 20px; margin-bottom: 20px; background: #fff; box-shadow: 0 3px 10px rgba(0,0,0,0.06); }");
            out.println("    .profile-section, .bidding-section { padding: 22px; border-radius: 12px; text-align: center; margin-bottom: 18px; box-shadow: 0 6px 18px rgba(0,0,0,0.08); }");
            out.println("    .profile-section { background: linear-gradient(135deg, #e6f7ec, #a8e6cf); border: 1px solid #7ac785; }");
            out.println("    .bidding-section { background: linear-gradient(135deg, #fff7e6, #ffe082); border: 1px solid #ffb74d; }");
            out.println("    .separator { height: 6px; background: linear-gradient(90deg, #2193b0, #6dd5ed); border-radius: 6px; margin: 20px 0; }");
            out.println("    table { width: 100%; border-collapse: collapse; margin-top: 10px; }");
            out.println("    th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; }");
            out.println("    th { background: #f0f0f0; }");
            out.println("    .soldout { color:red; font-weight:bold; background: #fff3f3; padding:8px; border-radius:6px; margin-bottom:10px; display:inline-block; }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class='container'>");
            out.println("    <div class='header'>");
            out.println("      <h2>RPL (R21 Premium League) Catalogue</h2>");
            out.println("    </div>");

            // Owner dropdown
            out.println("    <select id='ownerSelect' onchange='showOwnerDetails()'>");
            out.println("      <option value=''>-- Select Owner --</option>");
            ownerDataMap.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(owner -> out.printf("      <option value=\"%s\">%s</option>%n", escapeHtmlAttr(owner), escapeHtml(owner)));
            out.println("    </select>");
            out.println("    <div id='ownerArea'></div>");

            // Player dropdown
            out.println("    <select id='playerSelect' onchange='showDetails()'>");
            out.println("      <option value=''>-- Select Player --</option>");
            for (Player p : players) {
                if (p.name != null && !p.name.trim().isEmpty()) {
                    out.printf("      <option value=\"%s\">%s</option>%n", escapeHtmlAttr(p.name), escapeHtml(p.name));
                }
            }
            out.println("    </select>");
            out.println("    <div id='contentArea'></div>");

            // JS
            out.println("  <script>");
            out.println("    const IsAuctionData = " + IsAuctionData + ";");
            // --- Players data ---
            out.println("    const players = {");
            for (Player p : players) {
                out.printf("      \"%s\": { name: \"%s\", towerFlat: \"%s\", mobile: \"%s\", unavailability: \"%s\", role: \"%s\", photo: \"%s\", soldAt: \"%s\", toTeam: \"%s\", toOwner: \"%s\", ownerMobile: \"%s\", basePrice: \"%s\" },%n",
                        escapeJsKey(p.name), escapeJs(p.name), escapeJs(p.towerFlat), escapeJs(p.mobile),
                        escapeJs(p.unavailability), escapeJs(p.role), escapeJs(p.photoURL),
                        escapeJs(p.soldAt), escapeJs(p.toTeam), escapeJs(p.toOwner), escapeJs(p.ownerMobile),
                        escapeJs(p.basePrice));
            }
            out.println("    };");

            // --- Owners data ---
            out.println("    const owners = {");
            for (Map.Entry<String, OwnerData> entry : ownerDataMap.entrySet()) {
                out.printf("      \"%s\": { teamName: \"%s\", photoURL: \"%s\", basePrice: \"%s\", sheetData: %s },%n",
                        escapeJsKey(entry.getKey()), escapeJs(entry.getValue().teamName),
                        escapeJs(entry.getValue().photoURL), escapeJs(entry.getValue().basePrice),
                        toJsonArray(entry.getValue().sheetData));
            }
            out.println("    };");

            // --- JS Functions ---
            out.println("function showDetails() {");
            out.println("    const name = document.getElementById('playerSelect').value;");
            out.println("    const content = document.getElementById('contentArea');");
            out.println("    if (!name) { content.innerHTML = ''; return; }");
            out.println("    const p = players[name] || {};");
            out.println("    let profileHtml = '';");

            out.println("    if(IsAuctionData && p.soldAt && p.soldAt.trim() !== '') { profileHtml += `<div class='soldout'>SOLD OUT</div>`; }");
            out.println("    if (p.photo && p.photo.trim() !== '') {");
            out.println("        profileHtml += `<div style='position: relative; display: inline-block;'>`;");
            out.println("        profileHtml += `<span id='photoLoading' style='color: #ff5722; font-weight: bold;'>Please wait, your photo is coming...</span>`;");
            out.println("        profileHtml += `<img src='PlayersPhoto/${p.photo}' alt='${name}' style='display:block; max-width:180px; border-radius:12px; border:3px solid #fff; box-shadow:0 6px 14px rgba(0,0,0,0.12); margin-bottom:16px;' onload='document.getElementById(\"photoLoading\").style.display=\"none\";' onerror='document.getElementById(\"photoLoading\").innerText=\"Photo not available\";'>`;");
            out.println("        profileHtml += `</div>`;");
            out.println("    } else { profileHtml += `<img src='PlayersPhoto/Image_Not_Given.png' alt='No Photo Available'>`; }");

            out.println("    profileHtml += `<h3>Your Profile Info</h3>`;");
            out.println("    profileHtml += `<p><b>Name:</b> ${p.name || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Tower/Flat:</b> ${p.towerFlat || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Mobile:</b> ${p.mobile || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Unavailability:</b> ${p.unavailability || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Role:</b> ${p.role || ''}</p>`;");

            // BasePrice for players
            out.println("    if(!IsAuctionData && p.basePrice && p.basePrice.trim() !== '') {");
            out.println("        let base = Number(p.basePrice.replace(/,/g,''));");
            out.println("        let formattedBase = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(base);");
            out.println("        profileHtml += `<p><b>Base Price:</b> ${formattedBase}</p>`;");
            out.println("    }");

            out.println("    let biddingHtml = '<h3>Your Bidding Details</h3>';"); 
            out.println("    if (!p.soldAt || p.soldAt.trim() === '') {");
            out.println("        biddingHtml += `<p><b>Final Bid:</b> <span style='color:red'>This will be decided Post Auction. Auction is scheduled on 1st Nov</span></p>`;");
            out.println("    } else {");
            out.println("        let bidNumber = Number(p.soldAt.replace(/,/g, ''));");
            out.println("        let formattedBid = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(bidNumber);");
            out.println("        biddingHtml += `<p><b>Final Bid:</b> ${formattedBid}</p>`;");
            out.println("    }");
            out.println("    biddingHtml += `<p><b>Sold To Team:</b> ${p.toTeam || ''}</p>`;");
            out.println("    biddingHtml += `<p><b>Team Owner Name:</b> ${p.toOwner || ''}</p>`;");
            out.println("    biddingHtml += `<p><b>Team Owner Mobile:</b> ${p.ownerMobile || ''}</p>`;");
            out.println("    content.innerHTML = `<div id='profileSection' class='profile-section'>${profileHtml}</div><div class='separator'></div><div id='biddingSection' class='bidding-section'>${biddingHtml}</div>`;");
            out.println("}");

            // --- Owner JS Function ---
            out.println("function showOwnerDetails() {");
            out.println("    const ownerName = document.getElementById('ownerSelect').value;");
            out.println("    const ownerArea = document.getElementById('ownerArea');");
            out.println("    if (!ownerName) { ownerArea.innerHTML = ''; return; }");
            out.println("    const o = owners[ownerName] || {};");
            out.println("    let html = '';");
            out.println("    html += `<div class='profile-section'>`;");
            out.println("    html += `<img src='PlayersPhoto/${o.photoURL}' alt='Owner Photo' style='max-width:120px; border-radius:12px; margin-bottom:8px;'>`;");
            out.println("    html += `<h3>Owner: ${ownerName}</h3>`;");
            out.println("    html += `<p>Team: ${o.teamName || ''}</p>`;");

            // BasePrice for owner
            out.println("    if(!IsAuctionData && o.basePrice && o.basePrice.trim() !== '') {");
            out.println("        let base = Number(o.basePrice.replace(/,/g,''));");
            out.println("        let formattedBase = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(base);");
            out.println("        html += `<p><b>Base Price:</b> ${formattedBase}</p>`;");
            out.println("    }");

            out.println("    if(o.sheetData && o.sheetData.length > 0){");
            out.println("        html += `<table><tr><th>Sr. No</th><th>Player Name</th><th>Mobile</th><th>Bid Amount</th>` + (!IsAuctionData ? `<th>Base Price</th>` : '') + `</tr>`;");
            out.println("        o.sheetData.forEach((r, index) => {");
            out.println("            html += `<tr>`;");
            out.println("            html += `<td>${index+1}</td>`;");
            out.println("            html += `<td>${r['Name'] || ''}</td>`;");
            out.println("            html += `<td>${r['Mobile'] || ''}</td>`;");
            out.println("            let bid = r['BidAmount'] ? Number(r['BidAmount'].replace(/,/g,'')) : 0;");
            out.println("            let formattedBid = bid ? new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(bid) : '';");
            out.println("            html += `<td>${formattedBid}</td>`;");
            out.println("            if(!IsAuctionData){");
            out.println("                let base = r['BasePrice'] ? Number(r['BasePrice'].replace(/,/g,'')) : 0;");
            out.println("                let formattedBase = base ? new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(base) : '';");
            out.println("                html += `<td>${formattedBase}</td>`;");
            out.println("            }");
            out.println("            html += `</tr>`;");
            out.println("        });");
            out.println("        html += `</table>`;");
            out.println("    } else { if(IsAuctionData){ html += `<p>No data available for this owner.</p>`; } }");
            out.println("    html += `</div>`;");
            out.println("    ownerArea.innerHTML = html;");
            out.println("}");

            out.println("  </script>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Escape Methods & JSON Helper ---
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeHtmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String escapeJsKey(String s) {
        return escapeJs(Optional.ofNullable(s).orElse(""));
    }

    private static String toJsonArray(List<Map<String, String>> data) {
        if (data == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (Map<String, String> row : data) {
            sb.append("{");
            for (Map.Entry<String, String> e : row.entrySet()) {
                sb.append("\"").append(escapeJsKey(e.getKey())).append("\":\"").append(escapeJs(e.getValue())).append("\",");
            }
            if (!row.isEmpty()) sb.setLength(sb.length() - 1);
            sb.append("},");
        }
        if (!data.isEmpty()) sb.setLength(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }

    static class Player {
        String name = "";
        String towerFlat = "";
        String mobile = "";
        String unavailability = "";
        String photoURL = "";
        String role = "";
        String soldAt = "";
        String toTeam = "";
        String toOwner = "";
        String ownerMobile = "";
        String bidAmount = "";
        String basePrice = ""; // new
    }

    static class OwnerData {
        String teamName = "";
        String photoURL = "Image_Not_Given.png";
        String basePrice = ""; // new
        List<Map<String, String>> sheetData = new ArrayList<>();
    }
}
