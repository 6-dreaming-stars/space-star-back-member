package com.spacestar.back.member.service;

import com.spacestar.back.global.GlobalException;
import com.spacestar.back.global.ResponseStatus;
import com.spacestar.back.member.domain.Member;
import com.spacestar.back.member.domain.ProfileImage;
import com.spacestar.back.member.dto.req.MemberJoinReqDto;
import com.spacestar.back.member.dto.res.NicknameResDto;
import com.spacestar.back.member.repository.MemberRepository;
import com.spacestar.back.member.repository.ProfileImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.spacestar.back.member.enums.UnregisterType.BLACKLIST;
import static com.spacestar.back.member.enums.UnregisterType.MEMBER;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImp implements MemberService{

    private final MemberRepository memberRepository;
    private final ProfileImageRepository profileImageRepository;

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
                .isMain(true)
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
}
