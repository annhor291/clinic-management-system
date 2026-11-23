package com.example.clinic.triage;

// Mọi câu chữ hiển thị cho người dùng của tính năng gợi ý nằm ở MỘT chỗ này,
// để rà soát nội dung (đặc biệt các câu về an toàn) không phải đi tìm khắp code.
public class TriageMessages {

    private TriageMessages() {}

    public static final String DISCLAIMER =
            "Đây chỉ là gợi ý của hệ thống để giúp bạn đặt lịch khám dễ dàng hơn, "
                    + "không thay thế chẩn đoán của bác sĩ";

    // Lý do hiển thị khi Gate không đủ chắc chắn (không có luật leo thang nào khớp)
    public static final String LOW_CONFIDENCE_REASON =
            "Thông tin chưa đủ rõ để gợi ý chắc chắn một chuyên khoa";

    public static String emergency(String emergencyNumber) {
        return "Bạn đang có dấu hiệu có thể nguy hiểm. Hãy gọi cấp cứu " + emergencyNumber
                + " hoặc đến cơ sở y tế gần nhất NGAY. Không nên chờ đặt lịch khám.";
    }

    // Không ghi số đường dây nóng nào khác ngoài số cấp cứu đã cấu hình (tránh ghi sai số)
    public static String selfHarm(String emergencyNumber) {
        return "Chúng mình rất tiếc khi nghe bạn đang có những suy nghĩ này. Bạn không cần phải một mình đối mặt. "
                + "Hãy nói ngay với một người thân tin cậy. Nếu bạn cảm thấy không an toàn, "
                + "hãy gọi " + emergencyNumber + " hoặc đến cơ sở y tế gần nhất ngay lúc này.";
    }

    public static String refer(String clinicPhone, String emergencyNumber) {
        return "Hệ thống chưa thể gợi ý chắc chắn chuyên khoa phù hợp với thông tin bạn cung cấp. "
                + "Bạn vui lòng " + contact(clinicPhone) + " để được nhân viên tư vấn. "
                + dangerSigns(emergencyNumber);
    }

    public static String noDoctor(String clinicPhone, String emergencyNumber) {
        return "Hiện hệ thống chưa có bác sĩ phù hợp để gợi ý cho trường hợp này. "
                + "Bạn vui lòng " + contact(clinicPhone) + " để được nhân viên hỗ trợ đặt lịch. "
                + dangerSigns(emergencyNumber);
    }

    public static String pass(String specialtyName) {
        return "Bạn có thể đặt lịch khám chuyên khoa " + specialtyName + ".";
    }

    private static String contact(String clinicPhone) {
        return (clinicPhone == null || clinicPhone.isBlank())
                ? "liên hệ quầy tư vấn của phòng khám"
                : "gọi tổng đài phòng khám " + clinicPhone;
    }

    private static String dangerSigns(String emergencyNumber) {
        return "Nếu xuất hiện dấu hiệu nguy hiểm như khó thở nặng, đau ngực dữ dội, ngất, co giật "
                + "hoặc yếu liệt đột ngột, hãy gọi cấp cứu " + emergencyNumber + " ngay.";
    }
}
