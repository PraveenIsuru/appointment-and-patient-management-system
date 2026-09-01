package lk.icbt.dentalclinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * What a patient types when registering themselves.
 *
 * <p>A form object rather than the {@code Patient} entity. Binding a web request straight
 * onto an entity lets a caller set any field the entity exposes — {@code patientNo} or the
 * password hash — simply by adding a parameter. This class exposes only what the form should
 * be able to supply.
 *
 * <p>Validation lives here as annotations so the same rules apply to the HTML form and to the
 * REST API added in M6, rather than being written twice.
 */
public class PatientRegistrationForm {

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "Username may contain only letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
             message = "Password must contain an upper-case letter, a lower-case letter and a digit")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 120)
    private String email;

    /** Sri Lankan mobile or land line, with or without the +94 country code. */
    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^(?:0|\\+94)[1-9]\\d{8}$",
             message = "Enter a valid Sri Lankan number, for example 0771234567")
    private String contactNumber;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must be 255 characters or fewer")
    private String address;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @Size(max = 255, message = "Allergies must be 255 characters or fewer")
    private String allergies;

    /** Whether the two password fields agree. Checked by the service, not by an annotation. */
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    // ------------------------------------------------------------------ accessors ---

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }
}
