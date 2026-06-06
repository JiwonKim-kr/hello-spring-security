package kr.ac.hansung.controller;

import kr.ac.hansung.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DisplayName("UserController 테스트 (비밀번호 변경)")
class UserControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @MockitoBean
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(wac)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    @DisplayName("인증 사용자 - 비밀번호 변경 폼 조회 (200)")
    void passwordForm_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/user/password"))
            .andExpect(status().isOk())
            .andExpect(view().name("user/password"))
            .andExpect(model().attributeExists("passwordChangeDto"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("비인증 사용자 - 비밀번호 변경 폼 접근 시 로그인 리다이렉트")
    void passwordForm_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/user/password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    @DisplayName("비밀번호 변경 성공 후 /home 리다이렉트")
    void changePassword_success_redirectsToHome() throws Exception {
        willDoNothing().given(userService)
            .changePassword(eq("user@test.com"), eq("oldpass123"), eq("newpass123"));

        mockMvc.perform(post("/user/password")
                .with(csrf())
                .param("currentPassword", "oldpass123")
                .param("newPassword", "newpass123")
                .param("confirmPassword", "newpass123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/home"));

        then(userService).should().changePassword("user@test.com", "oldpass123", "newpass123");
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    @DisplayName("새 비밀번호 확인 불일치 시 폼 복귀 + 서비스 미호출")
    void changePassword_confirmMismatch_returnsForm() throws Exception {
        mockMvc.perform(post("/user/password")
                .with(csrf())
                .param("currentPassword", "oldpass123")
                .param("newPassword", "newpass123")
                .param("confirmPassword", "different123"))
            .andExpect(status().isOk())
            .andExpect(view().name("user/password"))
            .andExpect(model().attributeHasFieldErrors("passwordChangeDto", "confirmPassword"));

        then(userService).should(never()).changePassword(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    @DisplayName("현재 비밀번호 불일치 시 폼 복귀 (서비스 예외 처리)")
    void changePassword_wrongCurrent_returnsForm() throws Exception {
        willThrow(new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다."))
            .given(userService).changePassword(eq("user@test.com"), eq("wrongpass1"), eq("newpass123"));

        mockMvc.perform(post("/user/password")
                .with(csrf())
                .param("currentPassword", "wrongpass1")
                .param("newPassword", "newpass123")
                .param("confirmPassword", "newpass123"))
            .andExpect(status().isOk())
            .andExpect(view().name("user/password"))
            .andExpect(model().attributeHasFieldErrors("passwordChangeDto", "currentPassword"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    @DisplayName("새 비밀번호 8자 미만 검증 실패 시 폼 복귀")
    void changePassword_shortPassword_returnsForm() throws Exception {
        mockMvc.perform(post("/user/password")
                .with(csrf())
                .param("currentPassword", "oldpass123")
                .param("newPassword", "short")
                .param("confirmPassword", "short"))
            .andExpect(status().isOk())
            .andExpect(view().name("user/password"))
            .andExpect(model().attributeHasFieldErrors("passwordChangeDto", "newPassword"));

        then(userService).should(never()).changePassword(any(), any(), any());
    }
}
