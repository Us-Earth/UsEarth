package sparta.seed.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sparta.seed.domain.ClearMission;
import sparta.seed.domain.Community;
import sparta.seed.domain.Member;
import sparta.seed.domain.RefreshToken;
import sparta.seed.domain.dto.requestDto.MissionSearchCondition;
import sparta.seed.domain.dto.requestDto.RefreshTokenRequestDto;
import sparta.seed.domain.dto.requestDto.SocialMemberRequestDto;
import sparta.seed.domain.dto.responseDto.ClearMissionResponseDto;
import sparta.seed.domain.dto.responseDto.CommunityResponseDto;
import sparta.seed.domain.dto.responseDto.MemberResponseDto;
import sparta.seed.jwt.TokenProvider;
import sparta.seed.repository.ClearMissionRepository;
import sparta.seed.repository.CommunityRepository;
import sparta.seed.repository.MemberRepository;
import sparta.seed.repository.RefreshTokenRepository;
import sparta.seed.sercurity.UserDetailsImpl;
import sparta.seed.util.DateUtil;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {
  private final MemberRepository memberRepository;
  private final CommunityRepository communityRepository;
  private final ClearMissionRepository clearMissionRepository;
  private final DateUtil dateUtil;
  private final TokenProvider tokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  /**
   * 마이페이지
   */
  public ResponseEntity<MemberResponseDto> getMyPage(UserDetailsImpl userDetails) {
    Optional<Member> member = memberRepository.findById(userDetails.getId());

    double clearMission = clearMissionRepository.countAllByMemberId(member.get().getId());
    double missionDiv = clearMission / 5;
    String stringDiv = missionDiv +""; // split 해서 소수부만 뽑아주기 위해서 스트링으로 형변환 
    String[] split = stringDiv.split("\\."); // 소수점을 기준으로 스플릿

    MemberResponseDto memberResponseDto = MemberResponseDto.builder()
            .id(member.get().getId())
            .nickname(member.get().getNickname())
            .profileImage(member.get().getProfileImage())
            .totalClear((int) clearMission) // 미션 DB에서 멤버의 PK를 전부 카운팅해서 갯수를 리턴
            .level((int) (missionDiv + 1)) // 5개를 완료하면 레벨이 1오름 ... 1부터 시작한다고 할 때 만렙은 11이 될것
            .nextLevelExp(5 - (Integer.parseInt(split[1]) / 2)) //소수부를 2로 나눈 후 5에서 뺀 값이 남은 경험치
            .build();
    return ResponseEntity.ok().body(memberResponseDto);
  }

  /**
   * 닉네임 변경
   */
  @Transactional
  public ResponseEntity<Boolean> updateNickname(UserDetailsImpl userDetails, SocialMemberRequestDto requestDto) {
    Optional<Member> member = memberRepository.findById(userDetails.getId());
    if (member.get().getNickname().equals(requestDto.getNickname())) {
      return ResponseEntity.badRequest().body(false);
    }
    member.get().updateNickname(requestDto);
    return ResponseEntity.ok().body(true);
  }

  /**
   * 그룹미션 확인
   */
  public ResponseEntity<List<CommunityResponseDto>> showGroupMissionList(UserDetailsImpl userDetails) throws ParseException {
    List<Community> communityList = communityRepository.findByMemberId(userDetails.getId());
    List<CommunityResponseDto> responseDtoList = new ArrayList<>();
    for (Community community : communityList) {
      responseDtoList.add(CommunityResponseDto.builder()
              .communityId(community.getId())
              .createAt(String.valueOf(community.getCreatedAt()))
              .title(community.getTitle())
              .successPercent(community.getProofList().size() / community.getLimitScore() * 100) // 인증글 갯수에 비례한 달성도
              .isWriter(userDetails != null && community.getMemberId().equals(userDetails.getId())) // 내가 이 모임글의 작성자인지
              .dateStatus(getDateStatus(community)) // 모임이 시작전인지 시작했는지 종료되었는지
              .build());
    }
    return ResponseEntity.ok().body(responseDtoList);
  }

  /**
   * 미션 통계 - 주간 , 월간
   */

  /**
   * 일일 미션 달성 현황 확인
   */
  public ResponseEntity<ClearMissionResponseDto> targetDayMission(String targetDay, UserDetailsImpl userDetails) {

    LocalDate parseDay = LocalDate.parse(targetDay, DateTimeFormatter.ISO_DATE);
    System.out.println(parseDay);

    List<ClearMission> clearMissionList = clearMissionRepository.findAllByMemberIdAndCreatedAt(userDetails.getId(), parseDay);
    return ResponseEntity.ok(ClearMissionResponseDto.builder()
        .date(parseDay)
        .clearMissionList(clearMissionList)
        .clearMissionCnt(clearMissionList.size())
        .build());
  }

  private String getDateStatus(Community community) throws ParseException {
    return dateUtil.dateStatus(community.getStartDate(), community.getEndDate());
  }

  /**
   * 유저정보 공개 / 비공개 설정
   */
  @Transactional
  public ResponseEntity<Boolean> isSceret(UserDetailsImpl userDetails) {
    Optional<Member> member = memberRepository.findById(userDetails.getId());
    if (!member.get().isSecret()) {
      member.get().updateIsSecret(true);
      return ResponseEntity.ok().body(true);
    }
    member.get().updateIsSecret(false);
    return ResponseEntity.ok().body(false);
  }

  /**
   * 다른유저 정보 확인
   */
  public ResponseEntity<MemberResponseDto> getUserinfo(Long memberId) {
    Optional<Member> member = memberRepository.findById(memberId);
    if(!member.get().isSecret()){
      double clearMission = clearMissionRepository.countAllByMemberId(member.get().getId());
      double missionDiv = clearMission / 5;
      String stringDiv = missionDiv +"";
      String[] split = stringDiv.split("\\.");

      MemberResponseDto memberResponseDto = MemberResponseDto.builder()
          .id(member.get().getId())
          .nickname(member.get().getNickname())
          .profileImage(member.get().getProfileImage())
          .totalClear((int) clearMission)
          .level((int) (missionDiv + 1))
          .nextLevelExp(5 - (Integer.parseInt(split[1]) / 2))
//          .isFriend()
          .build();
      return ResponseEntity.ok().body(memberResponseDto);

    }else return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
  }


  /**
   * 리프레쉬토큰
   */
  @Transactional
  public ResponseEntity<MemberResponseDto> reissue(RefreshTokenRequestDto tokenRequestDto,UserDetailsImpl userDetails) {
    // 1. Refresh Token 검증
    if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
      throw new RuntimeException("Refresh Token 이 유효하지 않습니다.");
    }

    // 2. Access Token 에서 Member ID 가져오기
    Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

    // 3. 저장소에서 Member ID 를 기반으로 Refresh Token 값 가져옴
    RefreshToken refreshToken = refreshTokenRepository.findByRefreshKey(authentication.getName())
            .orElseThrow(() -> new RuntimeException("로그아웃 된 사용자입니다."));

    // 4. Refresh Token 일치하는지 검사
    if (!refreshToken.getRefreshValue().equals(tokenRequestDto.getRefreshToken())) {
      throw new RuntimeException("토큰의 유저 정보가 일치하지 않습니다.");
    }

    // 5. 새로운 토큰 생성
    MemberResponseDto memberResponseDto = tokenProvider.generateTokenDto(authentication,userDetails);

    // 6. 저장소 정보 업데이트
    RefreshToken newRefreshToken = refreshToken.updateValue(memberResponseDto.getRefreshToken());
    refreshTokenRepository.save(newRefreshToken);
    Member member = userDetails.getMember();
    MemberResponseDto responseDto = MemberResponseDto.builder()
            .id(member.getId())
            .username(member.getUsername())
            .nickname(member.getNickname())
            .accessToken(memberResponseDto.getAccessToken())
            .accessTokenExpiresIn(memberResponseDto.getAccessTokenExpiresIn())
            .grantType(memberResponseDto.getGrantType())
            .refreshToken(memberResponseDto.getRefreshToken())
            .build();

    // 토큰 발급
    return ResponseEntity.ok().body(responseDto);
  }

  public List<Long> getDailyMissionStats(MissionSearchCondition condition, UserDetailsImpl userDetails) {
    Long memberId = userDetails.getId();

    return clearMissionRepository.dailyMissionStats(condition,memberId);
  }
}