package sparta.seed.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sparta.seed.domain.dto.requestDto.ReplayRequestDto;
import sparta.seed.domain.dto.responseDto.ReplayResponseDto;
import sparta.seed.sercurity.UserDetailsImpl;
import sparta.seed.service.ReplayService;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReplayController {
	private final ReplayService replayService;

  /**
   * 글에 달린 인증글 전체 조회
   */
  @GetMapping("/api/replay/{CommunityId}")
	public List<ReplayResponseDto> getAllReplay(@PathVariable Long CommunityId,
                                              @RequestParam("page") int page,
                                              @RequestParam("size") int size,
                                              @AuthenticationPrincipal UserDetailsImpl userDetails){

	  page = page-1;
			return replayService.getAllReplay(CommunityId, page, size, userDetails);
	}

	/**
	 * 글에 달린 인증글 상세 조회
	 */
	@GetMapping("/api/replay/detail/{replayId}")
	public ReplayResponseDto getReplay(@PathVariable Long replayId, @AuthenticationPrincipal UserDetailsImpl userDetails){
		return replayService.getReplay(replayId, userDetails);
	}

  /**
   * 인증글 작성
   */
	@PostMapping(value = "/api/replay/{CommunityId}", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ReplayResponseDto createReplay(@PathVariable Long CommunityId,
	                                       @Valid @RequestPart(value = "dto") ReplayRequestDto replayRequestDto,
	                                       @RequestPart List<MultipartFile> multipartFile,
	                                       @AuthenticationPrincipal UserDetailsImpl userDetails) throws IOException {

		return replayService.createReplay(CommunityId, replayRequestDto, multipartFile, userDetails);
	}

  /**
   * 인증글 수정
   */

  /**
   * 인증글 삭제
   */
	@DeleteMapping("/api/replay/{replayId}")
	public Boolean deleteReplay(@PathVariable Long replayId, @AuthenticationPrincipal UserDetailsImpl userDetails){
		return replayService.deleteReplay(replayId, userDetails);
	}

	/**
	 * 전체 인증글 댓글 , 좋아요 갯수 조회
	 */
	@GetMapping("/api/replay/count/{CommunityId}")
	public List<ReplayResponseDto> countAllReplay(@PathVariable Long CommunityId){
		return replayService.countAllReplay(CommunityId);
	}

	/**
	 * 인증글 댓글 , 좋아요 갯수 조회
	 */
	@GetMapping("/api/replay/count/detail/{replayId}")
	public ReplayResponseDto countReplay(@PathVariable Long replayId){
		return replayService.countReplay(replayId);
	}

	/**
	 * 인증글 좋아요
	 */
	@PatchMapping("/api/replay/heart/{replayId}")
	public ReplayResponseDto heartReplay(@PathVariable Long replayId, @AuthenticationPrincipal UserDetailsImpl userDetails){
		return replayService.heartReplay(replayId, userDetails);
	}
}
