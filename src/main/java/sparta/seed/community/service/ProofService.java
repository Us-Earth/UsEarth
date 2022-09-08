package sparta.seed.community.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sparta.seed.community.controller.HeartRepository;
import sparta.seed.community.domain.Community;
import sparta.seed.community.domain.Heart;
import sparta.seed.community.domain.Proof;
import sparta.seed.community.domain.dto.requestdto.ProofRequestDto;
import sparta.seed.community.domain.dto.responsedto.ProofCountResponseDto;
import sparta.seed.community.domain.dto.responsedto.ProofHeartResponseDto;
import sparta.seed.community.domain.dto.responsedto.ProofResponseDto;
import sparta.seed.community.repository.CommunityRepository;
import sparta.seed.community.repository.ParticipantsRepository;
import sparta.seed.community.repository.ProofRepository;
import sparta.seed.img.domain.Img;
import sparta.seed.img.repository.ImgRepository;
import sparta.seed.s3.S3Dto;
import sparta.seed.s3.S3Uploader;
import sparta.seed.sercurity.UserDetailsImpl;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProofService {

	private final ProofRepository proofRepository;
	private final CommunityRepository communityRepository;

	private final ParticipantsRepository participantsRepository;
	private final HeartRepository heartRepository;
	private final ImgRepository imgRepository;
	private final S3Uploader s3Uploader;

	/**
	  글에 달린 인증글 조회
	 */
	public List<ProofResponseDto> getAllProof(Long communityId, int page, int size, UserDetailsImpl userDetails) {

		Sort.Direction direction = Sort.Direction.DESC;
		Sort sort = Sort.by(direction, "createdAt");
		Pageable pageable = PageRequest.of(page, size, sort);
		try {
		Page<Proof> replayList = proofRepository.findAllByCommunity_Id(communityId, pageable);
		List<ProofResponseDto> proofResponseDtoList = new ArrayList<>();
		for(Proof proof : replayList){
			proofResponseDtoList.add(ProofResponseDto.builder()
					.proofId(proof.getId())
					.title(proof.getTitle())
					.content(proof.getContent())
					.img(proof.getImgList())
					.commentCnt(proof.getCommentList().size())
					.heartCnt(proof.getHeartList().size())
					.writer(userDetails !=null && proof.getMemberId().equals(userDetails.getId()))
					.heart(userDetails !=null && heartRepository.existsByProofAndMemberId(proof, userDetails.getId()))
					.build());
		}
		return proofResponseDtoList;
		}catch (Exception e) {throw new IllegalArgumentException("해당 인증글이 존재하지 않습니다.");}
	}

	/**
	 * 글에 달린 인증글 상세 조회
	 */
	public ProofResponseDto getProof(Long proofId, UserDetailsImpl userDetails) {
		Proof proof = findTheProofById(proofId);
		return ProofResponseDto.builder()
				.proofId(proof.getId())
				.title(proof.getTitle())
				.content(proof.getContent())
				.img(proof.getImgList())
				.commentCnt(proof.getCommentList().size())
				.heartCnt(proof.getHeartList().size())
				.writer(userDetails !=null && proof.getMemberId().equals(userDetails.getId()))
				.heart(userDetails !=null && heartRepository.existsByProofAndMemberId(proof, userDetails.getId()))
				.build();
	}

	/**
	 * 인증글 작성
	 */
	public ResponseEntity<ProofResponseDto> createProof(Long communityId, ProofRequestDto proofRequestDto,
	                                                     List<MultipartFile> multipartFile, UserDetailsImpl userDetails) throws IOException {

		Long loginUserId = userDetails.getId();
		String nickname = userDetails.getNickname();
		Community community = communityRepository.findById(communityId)
				.orElseThrow(() -> new IllegalArgumentException("해당 게시물이 존재하지 않습니다."));

		Proof proof = Proof.builder()
				.memberId(loginUserId)
				.nickname(nickname)
				.title(proofRequestDto.getTitle())
				.content(proofRequestDto.getContent())
				.community(community)
				.build();

		if(participantsRepository.existsByCommunityAndMemberId(community, loginUserId)) {
			for (MultipartFile file : multipartFile) {
				S3Dto upload = s3Uploader.upload(file);
				Img findImage = Img.builder()
						.imgUrl(upload.getUploadImageUrl())
						.fileName(upload.getFileName())
						.proof(proof)
						.build();
				proof.addImg(findImage);
				imgRepository.save(findImage);
			}

			proofRepository.save(proof);

			ProofResponseDto proofResponseDto = ProofResponseDto.builder()
					.proofId(proof.getId())
					.title(proof.getTitle())
					.content(proof.getContent())
					.img(proof.getImgList())
					.commentCnt(proof.getCommentList().size())
					.heartCnt(proof.getHeartList().size())
					.writer(true)
					.heart(false)
					.build();
			return ResponseEntity.ok().body(proofResponseDto);
		}throw new IllegalArgumentException("캠페인 참가자만 인증글을 작성할 수 있습니다.");
	}

	/**
	 * 인증글 수정
	 */
	@Transactional
	public ResponseEntity<ProofResponseDto> updateProof(Long proofId, ProofRequestDto proofRequestDto,
	                                                    List<MultipartFile> multipartFile, UserDetailsImpl userDetails) throws IOException {
		Proof proof = findTheProofById(proofId);
		if(userDetails !=null && proof.getMemberId().equals(userDetails.getId())){
			proof.updateProof(proofRequestDto);

			if(multipartFile != null){
				int cnt = 0;
				for (MultipartFile file : multipartFile) {
					S3Dto upload = s3Uploader.upload(file);
					Optional<Img> targetImg = imgRepository.findById(proofRequestDto.getImgIdList()[cnt]);
					targetImg.get().updateImg(upload);
					cnt++;
				}
			}

			ProofResponseDto proofResponseDto = ProofResponseDto.builder()
					.proofId(proof.getId())
					.title(proof.getTitle())
					.content(proof.getContent())
					.img(proof.getImgList())
					.commentCnt(proof.getCommentList().size())
					.heartCnt(proof.getHeartList().size())
					.writer(true)
					.heart(false)
					.build();
			return ResponseEntity.ok().body(proofResponseDto);

		}throw new IllegalArgumentException("작성자만 수정할 수 있습니다.");
	}

	/**
	 * 인증글 삭제
	 */
	public Boolean deleteProof(Long proofId, UserDetailsImpl userDetails) {
		Proof proof = findTheProofById(proofId);

		if(userDetails !=null && proof.getMemberId().equals(userDetails.getId())){
			proofRepository.delete(proof);
			return true;
		}else return false;
	}

	/**
	 * 전체 인증글 댓글 , 좋아요 갯수 조회
	 */
	public List<ProofCountResponseDto> countAllProof(Long communityId) {
		List<Proof> proofList = proofRepository.findAllByCommunity_Id(communityId);
		List<ProofCountResponseDto> proofResponseDtoList = new ArrayList<>();
		for (Proof proof : proofList){
			proofResponseDtoList.add(ProofCountResponseDto.builder()
					.proofId(proof.getId())
					.commentCnt(proof.getCommentList().size())
					.heartCnt(proof.getHeartList().size())
					.build());
		}
		return proofResponseDtoList;
	}

	/**
	 * 인증글 댓글 , 좋아요 갯수 조회
	 */
	public ProofCountResponseDto countProof(Long proofId) {
		Proof proof = findTheProofById(proofId);
		return ProofCountResponseDto.builder()
				.proofId(proof.getId())
				.commentCnt(proof.getCommentList().size())
				.heartCnt(proof.getHeartList().size())
				.build();
	}

	/**
	 * 인증글 좋아요
	 */
	public ProofHeartResponseDto heartProof(Long proofId, UserDetailsImpl userDetails) {
		Proof proof = findTheProofById(proofId);
		Long loginUserId = userDetails.getId();

		if(!heartRepository.existsByProofAndMemberId(proof, loginUserId)){
			Heart heart = Heart.builder()
					.proof(proof)
					.memberId(loginUserId)
					.build();
			proof.addHeart(heart);
			heartRepository.save(heart);
			return ProofHeartResponseDto.builder()
					.proofId(proof.getId())
					.heart(true)
					.heartCnt(proof.getHeartList().size()).build();
		}else {
			Heart heart = heartRepository.findByProofAndMemberId(proof, loginUserId);
			proof.removeHeart(heart);
			heartRepository.delete(heart);
			return ProofHeartResponseDto.builder()
					.proofId(proof.getId())
					.heart(false)
					.heartCnt(proof.getHeartList().size()).build();
		}
	}

	private Proof findTheProofById(Long proofId) {
		return proofRepository.findById(proofId)
				.orElseThrow(() -> new IllegalArgumentException("해당 인증글이 존재하지 않습니다."));
	}

}
