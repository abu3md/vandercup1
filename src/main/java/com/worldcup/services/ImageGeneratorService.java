package com.worldcup.services;

import com.worldcup.models.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

public class ImageGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(ImageGeneratorService.class);
    private static ImageGeneratorService instance;

    private ImageGeneratorService() {}

    public static synchronized ImageGeneratorService getInstance() {
        if (instance == null) {
            instance = new ImageGeneratorService();
        }
        return instance;
    }

    public byte[] generateMatchCard(Match match) {
        try {
            // 1. Load the background template image from resources
            InputStream is = getClass().getResourceAsStream("/match_template.jpg");
            if (is == null) {
                logger.error("Template image '/match_template.jpg' not found in resources!");
                return null;
            }
            BufferedImage template = ImageIO.read(is);
            is.close();

            // 2. Load flags for both teams
            BufferedImage flag1 = downloadFlag(match.getTeam1Flag(), match.getTeam1());
            BufferedImage flag2 = downloadFlag(match.getTeam2Flag(), match.getTeam2());

            // 3. Create graphics and configure high quality rendering
            Graphics2D g2d = template.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 4. Draw flags (centered inside left/right boxes)
            // Left Box Center: X=208, Y=320. Size: 120x80
            g2d.drawImage(flag1, 208 - 60, 320 - 40, 120, 80, null);
            
            // Right Box Center: X=812, Y=320. Size: 120x80
            g2d.drawImage(flag2, 812 - 60, 320 - 40, 120, 80, null);

            // 5. Draw Country Names (centered horizontally below flags)
            // Configure font: SansSerif Bold, Size: 22
            Font font = new Font("SansSerif", Font.BOLD, 22);
            g2d.setFont(font);
            g2d.setColor(Color.WHITE);
            FontMetrics fm = g2d.getFontMetrics();

            // Draw Team 1 Name
            String name1 = match.getTeam1();
            int width1 = fm.stringWidth(name1);
            g2d.drawString(name1, 208 - (width1 / 2), 385);

            // Draw Team 2 Name
            String name2 = match.getTeam2();
            int width2 = fm.stringWidth(name2);
            g2d.drawString(name2, 812 - (width2 / 2), 385);

            // Clean up resources
            g2d.dispose();

            // 6. Convert to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(template, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();

            return imageBytes;
        } catch (Exception e) {
            logger.error("Error generating match card image", e);
            return null;
        }
    }

    public byte[] generateResultCard(Match match) {
        try {
            // 1. Load the background template image from resources
            InputStream is = getClass().getResourceAsStream("/result_template.jpg");
            if (is == null) {
                logger.error("Template image '/result_template.jpg' not found in resources!");
                return null;
            }
            BufferedImage template = ImageIO.read(is);
            is.close();

            // 2. Load flags for both teams
            BufferedImage flag1 = downloadFlag(match.getTeam1Flag(), match.getTeam1());
            BufferedImage flag2 = downloadFlag(match.getTeam2Flag(), match.getTeam2());

            // 3. Create graphics and configure high quality rendering
            Graphics2D g2d = template.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 4. Draw flags (centered inside left/right boxes)
            // Left Box Center: X=208, Y=320. Size: 120x80
            g2d.drawImage(flag1, 208 - 60, 320 - 40, 120, 80, null);
            
            // Right Box Center: X=812, Y=320. Size: 120x80
            g2d.drawImage(flag2, 812 - 60, 320 - 40, 120, 80, null);

            // 5. Draw Scores (centered horizontally below flags, replacing team names)
            // Configure font: SansSerif Bold, Size: 28 for clarity
            Font font = new Font("SansSerif", Font.BOLD, 28);
            g2d.setFont(font);
            g2d.setColor(Color.WHITE);
            FontMetrics fm = g2d.getFontMetrics();

            // Draw Team 1 Score
            String score1 = String.valueOf(match.getScore1() != null ? match.getScore1() : 0);
            int width1 = fm.stringWidth(score1);
            g2d.drawString(score1, 208 - (width1 / 2), 385);

            // Draw Team 2 Score
            String score2 = String.valueOf(match.getScore2() != null ? match.getScore2() : 0);
            int width2 = fm.stringWidth(score2);
            g2d.drawString(score2, 812 - (width2 / 2), 385);

            // Clean up resources
            g2d.dispose();

            // 6. Convert to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(template, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            baos.close();

            return imageBytes;
        } catch (Exception e) {
            logger.error("Error generating result card image", e);
            return null;
        }
    }

    private BufferedImage downloadFlag(String flagUrl, String teamName) {
        String countryCode = getCountryCode(teamName);
        String finalUrl = "https://flagcdn.com/w160/" + countryCode + ".png";

        if (flagUrl != null && !flagUrl.isEmpty() && !flagUrl.toLowerCase().endsWith(".svg")) {
            finalUrl = flagUrl;
        }

        try {
            logger.info("Downloading flag for team '{}' from URL: {}", teamName, finalUrl);
            return ImageIO.read(new URL(finalUrl));
        } catch (Exception e) {
            logger.warn("Failed to load flag for " + teamName + " from " + finalUrl + ", falling back to US flag", e);
            try {
                return ImageIO.read(new URL("https://flagcdn.com/w160/us.png"));
            } catch (Exception ex) {
                // Fallback to blank white image
                BufferedImage blank = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = blank.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 120, 80);
                g.dispose();
                return blank;
            }
        }
    }

    private String getCountryCode(String name) {
        if (name == null) return "us";
        String n = name.trim().toLowerCase();
        switch (n) {
            case "sa": case "saudi": case "saudi arabia": case "السعودية": case "السعوديه": return "sa";
            case "usa": case "us": case "united states": case "أمريكا": case "امريكا": case "الولايات المتحدة": return "us";
            case "ar": case "arg": case "argentina": case "الأرجنتين": case "الارجنتين": return "ar";
            case "fr": case "fra": case "france": case "فرنسا": return "fr";
            case "br": case "bra": case "brazil": case "البرازيل": return "br";
            case "de": case "ger": case "germany": case "ألمانيا": case "المانيا": return "de";
            case "jp": case "jpn": case "japan": case "اليابان": return "jp";
            case "ma": case "mar": case "morocco": case "المغرب": return "ma";
            case "hr": case "cro": case "croatia": case "كرواتيا": return "hr";
            case "es": case "esp": case "spain": case "إسبانيا": case "اسبانيا": return "es";
            case "pt": case "por": case "portugal": case "البرتغال": return "pt";
            case "mx": case "mex": case "mexico": case "المكسيك": return "mx";
            case "ca": case "can": case "canada": case "كندا": return "ca";
            case "qa": case "qatar": case "قطر": return "qa";
            case "it": case "ita": case "italy": case "إيطاليا": case "ايطاليا": return "it";
            case "nl": case "ned": case "netherlands": case "هولندا": return "nl";
            case "be": case "bel": case "belgium": case "بلجيكا": return "be";
            case "uy": case "uru": case "uruguay": case "الأوروغواي": case "الاوروغواي": return "uy";
            case "sn": case "sen": case "senegal": case "السنغال": return "sn";
            case "ch": case "sui": case "switzerland": case "سويسرا": return "ch";
            case "dk": case "den": case "denmark": case "الدنمارك": return "dk";
            case "pl": case "pol": case "poland": case "بولندا": return "pl";
            case "kr": case "kor": case "south korea": case "كوريا الجنوبية": case "كوريا": return "kr";
            case "au": case "aus": case "australia": case "أستراليا": case "استراليا": return "au";
            case "ec": case "ecu": case "ecuador": case "الإكوادور": case "الاكوادور": return "ec";
            case "tn": case "tun": case "tunisia": case "تونس": return "tn";
            case "cm": case "cmr": case "cameroon": case "الكاميرون": return "cm";
            case "gh": case "gha": case "ghana": case "غانا": return "gh";
            case "rs": case "srb": case "serbia": return "rs";
            case "ir": case "irn": case "iran": case "إيران": case "ايران": return "ir";
            case "eng": case "england": case "إنجلترا": case "انجلترا": return "gb-eng";
            case "wls": case "wales": case "ويلز": return "gb-wls";
            case "sco": case "scotland": case "اسكتلندا": return "gb-sct";
            case "za": case "rsa": case "south africa": case "جنوب أفريقيا": case "جنوب افريقيا": return "za";
            case "dz": case "alg": case "algeria": case "الجزائر": return "dz";
            case "at": case "aut": case "austria": case "النمسا": return "at";
            case "ba": case "bih": case "bosnia and herzegovina": case "البوسنة والهرسك": case "البوسنة": return "ba";
            case "co": case "col": case "colombia": case "كولومبيا": return "co";
            case "cw": case "cuw": case "curacao": case "curaçao": case "كوراساو": return "cw";
            case "cz": case "cze": case "czech republic": case "جمهورية التشيك": return "cz";
            case "cd": case "cod": case "democratic republic of the congo": case "جمهورية الكونغو الديمقراطية": case "الكونغو الديمقراطية": return "cd";
            case "eg": case "egy": case "egypt": case "مصر": return "eg";
            case "ht": case "hai": case "haiti": case "هايتي": return "ht";
            case "iq": case "irq": case "iraq": case "العراق": return "iq";
            case "ci": case "civ": case "ivory coast": case "كوت ديفوار": case "ساحل العاج": return "ci";
            case "jo": case "jor": case "jordan": case "الأردن": case "الاردن": return "jo";
            case "nz": case "nzl": case "new zealand": case "نيوزيلندا": case "نيوزيلنده": return "nz";
            case "no": case "nor": case "norway": case "النرويج": return "no";
            case "pa": case "pan": case "panama": case "بنما": return "pa";
            case "py": case "par": case "paraguay": case "باراغواي": return "py";
            case "se": case "swe": case "sweden": case "السويد": return "se";
            case "tr": case "tur": case "turkey": case "تركيا": return "tr";
            case "uz": case "uzb": case "uzbekistan": case "أوزبكستان": case "اوزبكستان": return "uz";
            case "sd": case "sud": case "sudan": case "السودان": return "sd";
            default:
                if (name.length() == 2) return name.toLowerCase();
                return "us"; // fallback
        }
    }
}
