package lk.icbt.dentalclinic.model.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Clinic staff member with full access — the "authorized staff" of the brief.
 */
@Entity
@Table(name = "administrators")
@PrimaryKeyJoinColumn(name = "id")
public class Administrator extends User {

    @Column(name = "staff_no", nullable = false, unique = true, length = 20)
    private String staffNo;

    @Column(name = "designation", nullable = false, length = 100)
    private String designation;

    protected Administrator() {
        // required by JPA
    }

    public Administrator(String username, String passwordHash, String email, String fullName,
                         String contactNumber, String staffNo, String designation) {
        super(username, passwordHash, email, fullName, contactNumber);
        this.staffNo = staffNo;
        this.designation = designation;
    }

    @Override
    public String getDashboardUrl() {
        return "/admin/dashboard";
    }

    @Override
    public String getDisplayRole() {
        return "Administrator";
    }

    public String getStaffNo() {
        return staffNo;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }
}
