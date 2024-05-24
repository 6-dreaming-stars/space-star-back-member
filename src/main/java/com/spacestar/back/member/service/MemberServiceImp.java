package com.spacestar.back.member.service;

import com.spacestar.back.global.GlobalException;
import com.spacestar.back.global.ResponseStatus;
import com.spacestar.back.member.domain.LikedGame;
import com.spacestar.back.member.domain.PlayGame;
import com.spacestar.back.member.dto.req.*;
import com.spacestar.back.member.jwt.JWTUtil;
import com.spacestar.back.member.domain.Member;
import com.spacestar.back.member.domain.ProfileImage;
import com.spacestar.back.member.dto.res.MemberLoginResDto;
import com.spacestar.back.member.dto.res.NicknameResDto;
import com.spacestar.back.member.repository.LikedGameRepository;
import com.spacestar.back.member.repository.MemberRepository;
import com.spacestar.back.member.repository.PlayGameRepository;
import com.spacestar.back.member.repository.ProfileImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.spacestar.back.member.enums.UnregisterType.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImp implements MemberService{

    private final MemberRepository memberRepository;
    private final ProfileImageRepository profileImageRepository;
    private final JWTUtil jwtUtil;
    private final LikedGameRepository likedGameRepository;
    private final PlayGameRepository playGameRepository;

    @Transactional
    @Override
    public void addMember(MemberJoinReqDto memberJoinReqDto) {

        Optional<Member> memberInfo = memberRepository.findByEmail(memberJoinReqDto.getEmail());
        if (memberInfo.isPresent()){

            //현재 가입한 회원
            if (memberInfo.get().getUnregister() == MEMBER){
                throw new GlobalException(ResponseStatus.DUPLICATED_MEMBERS);
            }
            //영구 탈퇴 회원
            if (memberInfo.get().getUnregister() == BLACKLIST){
                throw new GlobalException(ResponseStatus.BLACKLIST_MEMBER);
            }
        }

        Member member = Member.toEntity(memberJoinReqDto);
        memberRepository.save(member);

        ProfileImage profileImage = ProfileImage.builder()
                .member(member)
                .profileImageUrl(memberJoinReqDto.getImageUrl())
                .main(true)
                .idx(0)
                .build();

        profileImageRepository.save(profileImage);
    }

    @Override
    public NicknameResDto duplicationNickname(String nickname) {
        if (memberRepository.findByNickname(nickname).isPresent()){
            return NicknameResDto.builder()
                    .duplicated(true)
                    .message("닉네임이 중복되었습니다.")
                    .build();
        }
        return NicknameResDto.builder()
                .duplicated(false)
                .message("사용 가능한 닉네임입니다.")
                .build();
    }

    @Override
    public MemberLoginResDto kakaoLogin(MemberLoginReqDto memberLoginReqDto) {

        Member member = memberRepository.findByEmail(memberLoginReqDto.getEmail())
                .orElseThrow( () -> new GlobalException(ResponseStatus.NOT_EXIST_MEMBER));

        //영구 탈퇴 회원
        if (member.getUnregister() == BLACKLIST){
            throw new GlobalException(ResponseStatus.BLACKLIST_MEMBER);
        }
        //탈퇴 회원
        if (member.getUnregister() == DELETED){
            throw  new GlobalException(ResponseStatus.DELETE_MEMBER);
        }

        return MemberLoginResDto.builder()
                .accessToken("Bearer " + jwtUtil.createJwt(member.getUuid(),"ROLE_USER",3600000L))
                .build();

    }

    @Transactional
    @Override
    public void updateMemberInfo(String uuid,MemberInfoReqDto memberInfoReqDto) {

        Member member = memberRepository.findByUuid(uuid)
                .orElseThrow( () -> new GlobalException(ResponseStatus.NOT_EXIST_MEMBER));

        //회원 정보 수정
        memberRepository.save(Member.updateToEntity(member, memberInfoReqDto));

        //게임 관련 정보 수정
        //빈 리스트
        if (memberInfoReqDto.getLikedGameIds().isEmpty()){
            likedGameRepository.deleteAllByUuid(uuid);
        }
        else{
            //좋아하는 게임 삭제
            likedGameRepository.deleteAllByUuid(uuid);

            //좋아하는 게임 추가
            for (Long ids : memberInfoReqDto.getLikedGameIds()){

                LikedGame likeGame = LikedGame.builder()
                        .gameId(ids)
                        .uuid(uuid)
                        .build();
                likedGameRepository.save(likeGame);
            }
        }

        if (memberInfoReqDto.getPlayGames().isEmpty()){
            playGameRepository.deleteAllByUuid(uuid);
        }
        else{
            playGameRepository.deleteAllByUuid(uuid);
            for (MemberInfoGameReqDto memberInfoGameReqDto : memberInfoReqDto.getPlayGames()) {
                //메인 게임 수정
                if (Objects.equals(memberInfoReqDto.getMainGameId(), memberInfoGameReqDto.getGameId())) {
                    playGameRepository.save(PlayGame.updateMainToEntity(uuid, memberInfoGameReqDto));
                }
                else {
                    playGameRepository.save(PlayGame.updateGameToEntity(uuid, memberInfoGameReqDto));
                }
            }
        }
    }

    @Transactional
    @Override
    public void updateProfileImages(String uuid, List<ProfileImageReqDto> profileImageReqDtos) {

        Member member = memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new GlobalException(ResponseStatus.NOT_EXIST_MEMBER));

        List<ProfileImage> profileImages = profileImageRepository.findAllByMember(member);

        //사진 삭제
        for (ProfileImage profileImage : profileImages) {
            boolean check = false;
            for (ProfileImageReqDto profileImageReqDto : profileImageReqDtos) {
                if (profileImage.getProfileImageUrl().equals(profileImageReqDto.getProfileImageUrl())) {
                    check = true;
                    break;
                }
            }
            if (!check) {
                profileImageRepository.delete(profileImage);
            }
        }

        // 사진 저장
        for (ProfileImageReqDto profileImageReqDto : profileImageReqDtos) {
            boolean check = false;
            for (ProfileImage profileImage : profileImages) {
                //사진 존재
                if (profileImageReqDto.getProfileImageUrl().equals(profileImage.getProfileImageUrl())) {
                    profileImageRepository.save(ProfileImage.updateImage(profileImage, profileImageReqDto));
                    check = true;
                }
            }
            //사진 존재하지 않음
            if (!check){
                profileImageRepository.save(ProfileImage.addNewImage(member, profileImageReqDto));
            }
        }


    }
}
