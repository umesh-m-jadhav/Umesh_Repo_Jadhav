package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GeneratePlayerHtml {
	
	private static String OUTPUT_HTML_FILE = "RplPlayers.html";
	
	private static String baseFolder = "F:\\social\\AuctionCode\\CrikAuction\\";
	private static String defaultExcelPath = baseFolder + "DataFile\\Player_List.xlsx";
	private static  String auctionFolder = "C:\\Users\\Admin\\Downloads";
	private static String outputPath = baseFolder + OUTPUT_HTML_FILE;
    
	private static String localClonedRepoPath = "F:\\social\\AuctionCode\\CrikAuction\\Clone_Umesh_Repo_Jadhav";
	private static String remoteUrl = "https://github.com/umesh-m-jadhav/Umesh_Repo_Jadhav.git";
	private static final String GITHUB_API_URL = "https://api.github.com/repos/umesh-m-jadhav/Umesh_Repo_Jadhav/contents/";
	private static final String BRANCH = "main"; // branch to upload to
	private static ScheduledExecutorService scheduler;
	  
	private static String token = "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";
	// Calculate end time (current time + 5 hours in milliseconds)
	private static long endTime = System.currentTimeMillis() + 5 * 60 * 60 * 1000; // 5 hours
	private static boolean isSoldDataAvailable = false;
	private static boolean isAllPlayersSoldOut = false;
	
	private static boolean IsAuctionStarted = true;
	private static boolean IsAuctionData = true;
	private static boolean isUploadToGit = true;
	private static boolean testSupportNeeded = true;
	private static boolean isRefreshNeeded = false;
	
	public static void main(String[] args) {
		if(testSupportNeeded) {
			OUTPUT_HTML_FILE = "Test"+OUTPUT_HTML_FILE;
			outputPath = baseFolder + OUTPUT_HTML_FILE;
		}
		
		startScheduler();
		
//		while (System.currentTimeMillis() < endTime) {
// 			mainAuctionLogic();	
//            System.out.println("Finished at " + new Date() +System.lineSeparator());
//            
//            try {
//                Thread.sleep(10 * 1000); // 30 seconds
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
		
	}
    public static void mainAuctionLogic() {
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
        
        if(isUploadToGit) {
	        System.out.println("GIT upoaded started....");
	        //uploadFileToGit();
	        uploadFileToGitHubUsingRest();
	        System.out.println("GIT upoaded finished....");
        }
    }
    
    public static void startScheduler() {
        // Single-threaded scheduler (safe, lightweight)
        scheduler = Executors.newSingleThreadScheduledExecutor();

        Runnable uploadTask = new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("[" + new java.util.Date() + "] Starting GitHub upload task...");
                    //uploadFileToGitHubUsingRest();
                    mainAuctionLogic();
                    System.out.println("[" + new java.util.Date() + "] Upload task finished successfully.");
                } catch (Exception e) {
                    System.err.println("[" + new java.util.Date() + "] Upload failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        // Run immediately, then every 60 seconds (customize as needed)
        scheduler.scheduleAtFixedRate(uploadTask, 0, 60, TimeUnit.SECONDS);

        // Add shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopGitHubUploadScheduler()));
    }
    
    public static void stopGitHubUploadScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            System.out.println("Stopping GitHub upload scheduler...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                System.out.println("Scheduler stopped cleanly.");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }

    
    private static void uploadFileToGit() {
        Git git = null;
        try {
            File repoDir = new File(localClonedRepoPath);

            // Open existing repo or clone if not exists
            if (repoDir.exists()) {
                git = Git.open(repoDir);
            } else {
                git = Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(repoDir)
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider("umesh-m-jadhav", token))
                        .call();
            }

            // Path to the file in the repo
            Path targetPath = Paths.get(localClonedRepoPath, OUTPUT_HTML_FILE);

            // Overwrite file
            Files.copy(Paths.get(outputPath), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Force add the file (update index even if only content/case changed)
            git.add().addFilepattern(OUTPUT_HTML_FILE).setUpdate(true).call();

            // Commit, allowing empty commit to force push even if Git thinks no changes
            git.commit()
               .setMessage("Upload "+OUTPUT_HTML_FILE +" via Java + JGit (force overwrite)")
               .setAllowEmpty(true)  // <-- this allows commit even if git sees no changes
               .call();

            // Push to remote
            git.push()
               .setCredentialsProvider(new UsernamePasswordCredentialsProvider("umesh-m-jadhav", token))
               .setRemote("origin")
               .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main"))
               .call();

            System.out.println(OUTPUT_HTML_FILE + " uploaded successfully (force overwrite)!");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (git != null) git.close();
        }
    }

    private static void uploadFileToGitHubUsingRest() {
        HttpURLConnection getConn = null;
        HttpURLConnection conn = null;
        BufferedReader br = null;

        try {
            File file = new File(outputPath);
            if (!file.exists()) {
                System.out.println("File not found: " + outputPath);
                return;
            }

            // Read and encode file
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String encodedContent = Base64.getEncoder().encodeToString(fileContent);

            String getUrl = GITHUB_API_URL + OUTPUT_HTML_FILE;

            // Step 1: Check if file exists to get SHA
            getConn = (HttpURLConnection) new URL(getUrl).openConnection();
            getConn.setRequestProperty("Authorization", "token " + token);
            getConn.setRequestProperty("Accept", "application/vnd.github+json");

            String sha = null;
            if (getConn.getResponseCode() == 200) {
                br = new BufferedReader(new InputStreamReader(getConn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                br = null;

                String response = sb.toString();
                int shaIndex = response.indexOf("\"sha\":\"");
                if (shaIndex != -1) {
                    sha = response.substring(shaIndex + 7, response.indexOf("\"", shaIndex + 7));
                    System.out.println("Existing SHA: " + sha);
                }
            }

            // Step 2: Upload (create/update)
            String jsonBody = "{"
                    + "\"message\": \"Upload " + OUTPUT_HTML_FILE + " via Java REST API\","
                    + "\"branch\": \"" + BRANCH + "\","
                    + "\"content\": \"" + encodedContent + "\""
                    + (sha != null ? ",\"sha\": \"" + sha + "\"" : "")
                    + "}";

            conn = (HttpURLConnection) new URL(getUrl).openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Authorization", "token " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 201 || responseCode == 200) {
                System.out.println(OUTPUT_HTML_FILE + " uploaded successfully to GitHub!");
            } else {
                System.out.println("Failed to upload file. Response Code: " + responseCode);
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    String line;
                    while ((line = err.readLine()) != null) System.out.println(line);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Ensure everything closes
            try {
                if (br != null) br.close();
            } catch (IOException ignored) {}

            if (getConn != null) getConn.disconnect();
            if (conn != null) conn.disconnect();
        }
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
                if (IsAuctionData)
                	if(p.soldAt != null && p.soldAt.trim()!="") {
                		if(!isSoldDataAvailable ) {
                			isSoldDataAvailable= true;
                		}
                		if(p.soldAt.trim().equalsIgnoreCase("Yes")) {
                			isAllPlayersSoldOut = true;
                		}else {
                			isAllPlayersSoldOut = false;
                		}
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
            out.println("    th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; word-break: break-word; }");
            out.println("    th { background: #f0f0f0; }");
            out.println("    .soldout { color:red; font-weight:bold; background: #fff3f3; padding:8px; border-radius:6px; margin-bottom:10px; display:inline-block; }");
            out.println("    @media screen and (max-width: 600px) { table, th, td { font-size: 14px; padding: 4px 6px; } }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class='container'>");
            out.println("    <div class='header'>");
            out.println("      <h2>RPL (R21 Premium League) Catalogue</h2>");
            out.println("    </div>");

            // --- Auction Highlight ---
            if (IsAuctionStarted) {
            	if (isAllPlayersSoldOut) {
	                out.println("<div style='text-align:center; font-size:26px; font-weight:bold; color:white; " +
	                        "background: linear-gradient(90deg, #ff1744, #f50057); " +
	                        "padding:16px; border-radius:12px; margin:20px 0; box-shadow: 0 8px 24px rgba(0,0,0,0.25); " +
	                        "letter-spacing:1px;'>The auction is officially over! Every player has been successfully sold.</div>");
	            }else if (isSoldDataAvailable) {
	                out.println("<div style='text-align:center; font-size:26px; font-weight:bold; color:white; " +
	                        "background: linear-gradient(90deg, #ff1744, #f50057); " +
	                        "padding:16px; border-radius:12px; margin:20px 0; box-shadow: 0 8px 24px rgba(0,0,0,0.25); " +
	                        "letter-spacing:1px;'>Auction is started and in Progress</div>");
	            } else {
	                out.println("<div style='text-align:center; font-size:26px; font-weight:bold; color:white; " +
	                        "background: linear-gradient(90deg, #1976d2, #42a5f5); " +
	                        "padding:16px; border-radius:12px; margin:20px 0; box-shadow: 0 8px 24px rgba(0,0,0,0.25); " +
	                        "letter-spacing:1px;'>Auction is yet to start</div>");
	            }
            }
            
            // --- Owner dropdown ---
            out.println("    <select id='ownerSelect' onchange='showOwnerDetails()'>");
            out.println("      <option value=''>-- Select Team - Owner --</option>");
            ownerDataMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> {
                        String owner = entry.getKey();
                        String team = entry.getValue().teamName != null ? entry.getValue().teamName : "";
                        String display = team + " - " + owner;
                        out.printf("      <option value=\"%s\">%s</option>%n", escapeHtmlAttr(owner), escapeHtml(display));
                    });
            out.println("    </select>");
            out.println("    <div id='ownerArea'></div>");

            // --- Player dropdown ---
            out.println("    <select id='playerSelect' onchange='showDetails()'>");
            out.println("      <option value=''>-- Select Player --</option>");
            for (Player p : players) {
                if (p.name != null && !p.name.trim().isEmpty()) {
                    out.printf("      <option value=\"%s\">%s</option>%n", escapeHtmlAttr(p.name), escapeHtml(p.name));
                }
            }
            out.println("    </select>");
            out.println("    <div id='contentArea'></div>");

            // --- JS ---
            out.println("  <script>");
            out.println("    const IsAuctionData = " + IsAuctionData + ";");
            
            // Players data
            out.println("    const players = {");
            for (Player p : players) {
                out.printf("      \"%s\": { name: \"%s\", towerFlat: \"%s\", mobile: \"%s\", unavailability: \"%s\", role: \"%s\", photo: \"%s\", soldAt: \"%s\", toTeam: \"%s\", toOwner: \"%s\", ownerMobile: \"%s\", basePrice: \"%s\" },%n",
                        escapeJsKey(p.name), escapeJs(p.name), escapeJs(p.towerFlat), escapeJs(p.mobile),
                        escapeJs(p.unavailability), escapeJs(p.role), escapeJs(p.photoURL),
                        escapeJs(p.soldAt), escapeJs(p.toTeam), escapeJs(p.toOwner), escapeJs(p.ownerMobile),
                        escapeJs(p.basePrice));
            }
            out.println("    };");

            // Owners data
            out.println("    const owners = {");
            for (Map.Entry<String, OwnerData> entry : ownerDataMap.entrySet()) {
                out.printf("      \"%s\": { teamName: \"%s\", photoURL: \"%s\", basePrice: \"%s\", sheetData: %s },%n",
                        escapeJsKey(entry.getKey()), escapeJs(entry.getValue().teamName),
                        escapeJs(entry.getValue().photoURL), escapeJs(entry.getValue().basePrice),
                        toJsonArray(entry.getValue().sheetData));
            }
            out.println("    };");

            // Show player details
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
            out.println("    profileHtml += `<h3>Your Profile Info.</h3>`;");
            out.println("    profileHtml += `<p><b>Name:</b> ${p.name || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Tower/Flat:</b> ${p.towerFlat || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Mobile:</b> ${p.mobile || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Unavailability:</b> ${p.unavailability || ''}</p>`;");
            out.println("    profileHtml += `<p><b>Role:</b> ${p.role || ''}</p>`;");
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
            
         // --- Owner JS Function (Horizontal Cards) ---
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
            out.println("    if(!IsAuctionData && o.basePrice && o.basePrice.trim() !== '') {");
            out.println("        let base = Number(o.basePrice.replace(/,/g,''));");
            out.println("        let formattedBase = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(base);");
            out.println("        html += `<p><b>Base Price:</b> ${formattedBase}</p>`;");
            out.println("    }");
            out.println("    if(o.sheetData && o.sheetData.length > 0){");
            out.println("        html += `<div style='display:flex; flex-wrap:wrap; gap:12px; margin-top:16px;'>`;"); // flex container
            out.println("        o.sheetData.forEach((r, index) => {");
            out.println("            let bid = r['BidAmount'] ? Number(r['BidAmount'].replace(/,/g,'')) : 0;");
            out.println("            let formattedBid = bid ? new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(bid) : '';");
            out.println("            let base = r['BasePrice'] ? Number(r['BasePrice'].replace(/,/g,'')) : 0;");
            out.println("            let formattedBase = base ? new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(base) : '';");
            out.println("            let mobileMasked = r['Mobile'] || '';");
            out.println("            if(mobileMasked.length > 4) {");
            out.println("                mobileMasked = '*'.repeat(mobileMasked.length - 4) + mobileMasked.slice(-4);");
            out.println("            }");
            out.println("            let unavailability = r['Unavailability'] || '';");  // <-- new row

            out.println("            html += `<div style='flex:1 1 200px; border:1px solid #ccc; border-radius:10px; padding:10px; background:#fff; box-shadow:0 4px 12px rgba(0,0,0,0.1); font-size:14px;'>`;");
            out.println("            html += `<p style='margin:2px 0;'><b>Player No:</b> ${index+1}</p>`;");
            out.println("            html += `<p style='margin:2px 0;'><b>Name:</b> ${r['Name'] || ''}</p>`;");
            out.println("            html += `<p style='margin:2px 0;'><b>Mobile:</b> ${mobileMasked}</p>`;");
            out.println("            html += `<p style='margin:2px 0;'><b>Bid Amount:</b> ${formattedBid}</p>`;");
            out.println("            if(!IsAuctionData) html += `<p style='margin:2px 0;'><b>Base Price:</b> ${formattedBase}</p>`;");
            out.println("            html += `<p style='margin:2px 0;'><b>Unavailability:</b> ${unavailability}</p>`;"); // new row
            out.println("            html += `</div>`;"); // end player card
            out.println("        });");
            out.println("        html += `</div>`;"); // end flex container
            out.println("    } else { if(IsAuctionData){ html += `<p>No data available for this owner.</p>`; } }");
            out.println("    html += `</div>`;");
            out.println("    ownerArea.innerHTML = html;");
            out.println("}");

            
         // --- Sold Out Players Section ---
            out.println("if(IsAuctionData) {");
            out.println("    const soldOutArea = document.createElement('div');");
            out.println("    soldOutArea.style.marginTop = '40px';");
            out.println("    soldOutArea.innerHTML = `<h3 style='text-align:center; color:red; margin-bottom:20px;'>Sold Out Players</h3>`;");

            out.println("    const soldContainer = document.createElement('div');");
            out.println("    soldContainer.style.display = 'flex';");
            out.println("    soldContainer.style.flexWrap = 'wrap';");
            out.println("    soldContainer.style.justifyContent = 'center';"); // center horizontally
            out.println("    soldContainer.style.gap = '16px';");

            out.println("    Object.values(players).forEach(p => {");
            out.println("        if(p.soldAt && p.soldAt.trim() !== '' && p.toTeam && p.toTeam.trim() !== '') {"); // sold players
            out.println("            const card = document.createElement('div');");
            out.println("            card.style.position = 'relative';");
            out.println("            card.style.width = '180px';");
            out.println("            card.style.border = '1px solid #ccc';");
            out.println("            card.style.borderRadius = '10px';");
            out.println("            card.style.overflow = 'hidden';");
            out.println("            card.style.textAlign = 'center';");
            out.println("            card.style.boxShadow = '0 4px 12px rgba(0,0,0,0.1)';");
            out.println("            card.style.padding = '8px';");
            out.println("            card.style.background = '#fff';");

            out.println("            let photo = p.photo && p.photo.trim() !== '' ? p.photo : 'Image_Not_Given.png';");
            out.println("            let formattedBid = p.soldAt ? new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(Number(p.soldAt.replace(/,/g,''))) : '';");

            out.println("            card.innerHTML = `<p style='margin:4px 0; font-weight:bold;'>${p.name}</p>` +");
            out.println("                             `<div style='position:relative;'><img src='PlayersPhoto/${photo}' style='width:100%; border-radius:8px;'>` +");
            out.println("                             `<div style='position:absolute; top:0; left:-40px; transform:rotate(-45deg); width:200%; text-align:center; background:rgba(255,0,0,0.7); color:white; font-weight:bold; font-size:16px;'>SOLD</div></div>` +");
            out.println("                             `<p style='margin:4px 0; font-size:13px;'><b>Sold @:</b> ${formattedBid}</p>` +");
            out.println("                             `<p style='margin:2px 0; font-size:13px;'><b>Sold To:</b> ${p.toTeam || ''}</p>` +");
            out.println("                             `<p style='margin:2px 0; font-size:13px;'><b>Team Owner:</b> ${p.toOwner || ''}</p>`;");
            out.println("            soldContainer.appendChild(card);");
            out.println("        }");
            out.println("    });");

            out.println("    soldOutArea.appendChild(soldContainer);");
            out.println("    document.body.appendChild(soldOutArea);");
            out.println("}");
            
            out.println("  </script>");
            out.println("</body>");
            out.println("</html>");
            if (IsAuctionData && isRefreshNeeded && !isAllPlayersSoldOut) {
	         // --- Auto Refresh Page Content Every 2 Seconds (Without Reload) ---
            	out.println("<script>");
            	out.println("function refreshPageContent() {");
            	out.println("    var xhr = new XMLHttpRequest();");
            	out.println("    xhr.open('GET', window.location.href, true);");
            	out.println("    xhr.setRequestHeader('Cache-Control', 'no-cache');");
            	out.println("    xhr.onreadystatechange = function() {");
            	out.println("        if (xhr.readyState === 4 && xhr.status === 200) {");
            	out.println("            var parser = new DOMParser();");
            	out.println("            var doc = parser.parseFromString(xhr.responseText, 'text/html');");
            	out.println("            var newBody = doc.getElementsByTagName('body')[0];");
            	out.println("            if (newBody) {");
            	out.println("                document.body.innerHTML = newBody.innerHTML;");
            	out.println("                console.log('1>Page content updated at ' + new Date().toLocaleTimeString());");
            	out.println("                // Force image reloads");
            	out.println("                var imgs = document.getElementsByTagName('img');");
            	out.println("                for (var i = 0; i < imgs.length; i++) {");
            	out.println("                    var src = imgs[i].src;");
            	out.println("                    imgs[i].src = src.split('?')[0] + '?t=' + new Date().getTime();");
            	out.println("                }");
            	out.println("            }");
            	out.println("        }");
            	out.println("    };");
            	out.println("    xhr.send();");
            	out.println("}");
            	out.println("setInterval(refreshPageContent, 10000);");
            	out.println("</script>");

            }

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
