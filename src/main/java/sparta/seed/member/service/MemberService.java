package sparta.seed.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sparta.seed.community.domain.Community;
import sparta.seed.community.domain.dto.responsedto.CommunityMyJoinResponseDto;
import sparta.seed.community.repository.ParticipantsRepository;
import sparta.seed.community.repository.ProofRepository;
import sparta.seed.community.service.SlangService;
import sparta.seed.exception.CustomException;
import sparta.seed.exception.ErrorCode;
import sparta.seed.jwt.TokenProvider;
import sparta.seed.member.domain.Member;
import sparta.seed.member.domain.dto.requestdto.NicknameRequestDto;
import sparta.seed.member.domain.dto.responsedto.NicknameResponseDto;
import sparta.seed.member.domain.dto.responsedto.UserInfoResponseDto;
import sparta.seed.member.repository.MemberRepository;
import sparta.seed.mission.domain.ClearMission;
import sparta.seed.mission.domain.dto.requestdto.MissionSearchCondition;
import sparta.seed.mission.domain.dto.responsedto.ClearMissionResponseDto;
import sparta.seed.mission.repository.ClearMissionRepository;
import sparta.seed.msg.ResponseMsg;
import sparta.seed.s3.S3Uploader;
import sparta.seed.login.UserDetailsImpl;
import sparta.seed.util.DateUtil;
import sparta.seed.util.ExpUtil;
import sparta.seed.util.RedisService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
  private final MemberRepository memberRepository;
  private final ClearMissionRepository clearMissionRepository;
  private final ProofRepository proofRepository;
  private final ParticipantsRepository participantsRepository;
  private final RedisService redisService;
  private final SlangService slangService;
  private final TokenProvider tokenProvider;
  private final S3Uploader s3Uploader;
  private final DateUtil dateUtil;
  private final ExpUtil expUtil;
  public static final String BEARER_PREFIX = "Bearer ";
  public static final String AUTHORIZATION_HEADER = "Authorization";

  /**
   * 마이페이지
   */
  public ResponseEntity<UserInfoResponseDto> getMyPage(UserDetailsImpl userDetails) {
    Member member = memberRepository.findById(userDetails.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.UNKNOWN_USER));

    return getUserInfo(member);
  }

  /**
   * 닉네임 중복체크
   */
  public ResponseEntity<Boolean> checkNickname(NicknameRequestDto requestDto) {
    if (memberRepository.existsByNickname(requestDto.getNickname())) {
      return ResponseEntity.ok().body(false);
    } else return ResponseEntity.ok().body(true);
  }

  /**
   * 닉네임 변경
   */
  @Transactional
  public ResponseEntity<NicknameResponseDto> updateNickname(UserDetailsImpl userDetails, NicknameRequestDto requestDto) {
    Member member = memberRepository.findById(userDetails.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.UNKNOWN_USER));
    slangService.checkSlang(requestDto.getNickname());
    if (!(member.getNickname().equals(requestDto.getNickname()) && memberRepository.existsByNickname(requestDto.getNickname()))) {
      member.updateNickname(requestDto);
      return ResponseEntity.ok().body(NicknameResponseDto.builder()
              .nickname(member.getNickname())
              .success(true)
              .build());
    }
    throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
  }

  /**
   * 그룹미션 확인
   */
  public ResponseEntity<List<CommunityMyJoinResponseDto>> showGroupMissionList(UserDetailsImpl userDetails) {
    try {
      List<Community> communityList = memberRepository.getCommunityBelongToMember(userDetails.getId());
      List<CommunityMyJoinResponseDto> responseDtoList = new ArrayList<>();
      for (Community community : communityList) {
        Long certifiedProof = countOfCertifiedProofBy(community);
        responseDtoList.add(CommunityMyJoinResponseDto.builder()
                .communityId(community.getId())
                .title(community.getTitle())
                .img(community.getImg())
                .writer(userDetails.getId().equals(community.getMemberId()))
                .currentPercent(((double) community.getParticipantsList().size() / community.getLimitParticipants()) * 100)
                .successPercent(((double) certifiedProof / community.getLimitScore()) * 100)
                .startDate(community.getStartDate())
                .endDate(community.getEndDate())
                .dateStatus(getDateStatus(community))
                .build());
      }
      return ResponseEntity.ok().body(responseDtoList);
    } catch (Exception e) {
      throw new CustomException(ErrorCode.UNKNOWN_USER);
    }
  }

  /**
   * 미션 통계 - 주간 , 월간
   */
  public List<ClearMissionResponseDto> getDailyMissionStats(MissionSearchCondition condition, UserDetailsImpl userDetails) {
    Long memberId = userDetails.getId();

    return clearMissionRepository.dailyMissionStats(condition, memberId);
  }

  /**
   * 일일 미션 달성 현황 확인
   */
  public ResponseEntity<ClearMissionResponseDto> targetDayMission(String selectedDate, UserDetailsImpl userDetails) {
    try {
      List<ClearMission> clearMissionList = clearMissionRepository.findAllByMemberIdAndClearTime(userDetails.getId(), selectedDate);
      return ResponseEntity.ok(ClearMissionResponseDto.builder()
              .clearTime(selectedDate)
              .clearMissionList(clearMissionList)
              .count(clearMissionList.size())
              .build());
    } catch (Exception e) {
      throw new CustomException(ErrorCode.UNKNOWN_USER);
    }
  }

  private String getDateStatus(Community community) throws ParseException {
    return dateUtil.dateStatus(community.getStartDate(), community.getEndDate());
  }

  /**
   * 유저정보 공개 / 비공개 설정
   */
  @Transactional
  public ResponseEntity<Boolean> isSceret(UserDetailsImpl userDetails) {
    Member member = memberRepository.findById(userDetails.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.UNKNOWN_USER));
    if (!member.isSecret()) {
      member.updateIsSecret(true);
      return ResponseEntity.ok().body(true);
    }
    member.updateIsSecret(false);
    return ResponseEntity.ok().body(false);
  }

  /**
   * 다른유저 정보 확인
   */
  public ResponseEntity<UserInfoResponseDto> getUserinfo(Long memberId) {
    Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(ErrorCode.UNKNOWN_USER));
    if (!member.isSecret()) {
      return getUserInfo(member);

    }
    throw new CustomException(ErrorCode.CLOSED_USER);
  }

  /**
   * 리프레쉬토큰
   */
  @Transactional
  public ResponseEntity<String> reissue(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken = request.getHeader("refreshToken").substring(BEARER_PREFIX.length());
    String memberId = request.getHeader("memberId");

    if (!tokenProvider.validateToken(refreshToken)) {
      throw new CustomException(ErrorCode.BE_NOT_VALID_TOKEN);
    }
    Member member = memberRepository.findById(Long.valueOf(memberId)).orElseThrow(() -> new CustomException(ErrorCode.MEMBER_MISMATCH));
    String accessToken = tokenProvider.generateAccessToken(memberId, member.getNickname(), member.getAuthority().toString());
    response.setHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken);
    return ResponseEntity.ok().body(ResponseMsg.ISSUANCE_SUCCESS.getMsg());
  }

  /**
   * 로그아웃
   */
  public ResponseEntity<String> logout(UserDetailsImpl userDetails) {
    if (userDetails == null) {
      throw new CustomException(ErrorCode.UNKNOWN_ERROR);
    }
    try {
      redisService.deleteValues(String.valueOf(userDetails.getId()));
      return ResponseEntity.ok().body(ResponseMsg.LOGOUT_SUCCESS.getMsg());
    } catch (EmptyResultDataAccessException e) {
      throw new CustomException(ErrorCode.NEED_A_LOGIN);
    }
  }

  /**
   * 프로필 이미지 변경
   */
  @Transactional
  public ResponseEntity<Boolean> changeProfileImage(UserDetailsImpl userDetails, MultipartFile multipartFile) throws IOException {
    Member member = memberRepository.findById(userDetails.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.UNKNOWN_USER));

    if (multipartFile == null) {
      throw new CustomException(ErrorCode.NOT_FOUND_IMG);
    }
    member.changeProfileImage(s3Uploader.upload(multipartFile).getUploadImageUrl());
    return ResponseEntity.ok().body(true);
  }

  /**
   * 회월 탈퇴
   */
  @Transactional
  public ResponseEntity<String> withdrawal(UserDetailsImpl userDetails) {
    participantsRepository.deleteByMemberId(userDetails.getId());
    clearMissionRepository.deleteByMemberId(userDetails.getId());
    memberRepository.deleteById(userDetails.getId());
    return ResponseEntity.ok().body(ResponseMsg.WITHDRAWAL_SUCCESS.getMsg());
  }


  // 유저 정보 뽑기
  private ResponseEntity<UserInfoResponseDto> getUserInfo(Member member) {
    double clearMission = clearMissionRepository.countAllByMemberId(member.getId());
    Integer needNextLevelExp = expUtil.getNextLevelExp().get(member.getLevel());

    UserInfoResponseDto userInfoResponseDto = UserInfoResponseDto.builder()
            .id(member.getId())
            .nickname(member.getNickname())
            .username(member.getUsername())
            .profileImage(member.getProfileImage())
            .level(member.getLevel())
            .totalClear((int) clearMission)
            .nextLevelExp(member.getExp())
            .needNextLevelExp(needNextLevelExp)
            .isSecret(member.isSecret())
            .loginType(member.getLoginType())
            .build();
    return ResponseEntity.ok().body(userInfoResponseDto);
  }

  private Long countOfCertifiedProofBy(Community community) {
    if (community.getParticipantsList().size() >= 2) {
      return proofRepository.countOfCertifiedProofByMoreThanTwoPeople(community);
    } else {
      return proofRepository.countOfCertifiedProofByOnePeople(community);
    }
  }
}