package sparta.seed.domain.dto.responseDto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MemberResponseDto {
    private Long id;
    private String grantType;
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    private String nickname;
    private String username;
    private String socialId;
    private String profileImage;
    private Integer level;
    private Integer exp;

    @Builder

    public MemberResponseDto(Long id, String grantType, String accessToken, String refreshToken, Long accessTokenExpiresIn, String nickname, String username, String socialId, String profileImage, Integer level, Integer exp) {
        this.id = id;
        this.grantType = grantType;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.nickname = nickname;
        this.username = username;
        this.socialId = socialId;
        this.profileImage = profileImage;
        this.level = level;
        this.exp = exp;
    }
}

