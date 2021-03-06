package br.edu.ulbra.election.candidate.service;

import br.edu.ulbra.election.candidate.client.ElectionClientService;
import br.edu.ulbra.election.candidate.client.PartyClientService;
import br.edu.ulbra.election.candidate.exception.GenericOutputException;
import br.edu.ulbra.election.candidate.input.v1.CandidateInput;
import br.edu.ulbra.election.candidate.model.Candidate;
import br.edu.ulbra.election.candidate.output.v1.*;
import br.edu.ulbra.election.candidate.repository.CandidateRepository;
import feign.FeignException;
import org.apache.commons.lang.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.transform.Result;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final ElectionClientService electionClientService;
    private final PartyClientService partyClientService;

    private final ModelMapper modelMapper;

    private static final String MESSAGE_INVALID_ID = "Invalid id";
    private static final String MESSAGE_INVALID_ELECTION_ID = "Invalid Election Id";
    private static final String MESSAGE_CANDIDATE_NOT_FOUND = "Candidate not found";

    @Autowired
    public CandidateService(CandidateRepository candidateRepository, ModelMapper modelMapper, ElectionClientService electionClientService, PartyClientService partyClientService) {
        this.candidateRepository = candidateRepository;
        this.modelMapper = modelMapper;
        this.electionClientService = electionClientService;
        this.partyClientService = partyClientService;
    }

    public List<CandidateOutput> getAll() {
        List<Candidate> candidateList = (List<Candidate>) candidateRepository.findAll();
        List<CandidateOutput> candidateOutputList = candidateList.stream().map(this::toCandidateOutput).collect(Collectors.toList());
        for (int i = 0; i < candidateOutputList.size(); i++) {
            try {
                PartyOutput partyOutput = partyClientService.getById(candidateList.get(i).getPartyId());
                ElectionOutput electionOutput = electionClientService.getById(candidateList.get(i).getElectionId());

                candidateOutputList.get(i).setPartyOutput(partyOutput);
                candidateOutputList.get(i).setElectionOutput(electionOutput);
            } catch (FeignException e) {
                if (e.status() == 500) {
                    throw new GenericOutputException("Invalid Party or Election");
                }
            }
        }
        return candidateOutputList;
    }

    public CandidateOutput create(CandidateInput candidateInput) {
        validateInput(candidateInput);
        validateDuplicate(candidateInput, null);
        Candidate candidate = modelMapper.map(candidateInput, Candidate.class);
        candidate = candidateRepository.save(candidate);
        return toCandidateOutput(candidate);
    }

    public CandidateOutput getById(Long candidateId) {
        if (candidateId == null) {
            throw new GenericOutputException(MESSAGE_INVALID_ID);
        }

        Candidate candidate = candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            throw new GenericOutputException(MESSAGE_CANDIDATE_NOT_FOUND);
        }
        CandidateOutput candidateOutput = modelMapper.map(candidate, CandidateOutput.class);
        try {
            PartyOutput partyOutput = partyClientService.getById(candidate.getPartyId());
            ElectionOutput electionOutput = electionClientService.getById(candidate.getElectionId());

            candidateOutput.setPartyOutput(partyOutput);
            candidateOutput.setElectionOutput(electionOutput);
        } catch (FeignException e) {
            if (e.status() == 500) {
                throw new GenericOutputException("Invalid Party or Election");
            }
        }

        return candidateOutput;
    }

    public CandidateOutput update(Long candidateId, CandidateInput candidateInput) {
        if (candidateId == null) {
            throw new GenericOutputException(MESSAGE_INVALID_ID);
        }
        validateInput(candidateInput);
        validateDuplicate(candidateInput, candidateId);

        Candidate candidate = candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            throw new GenericOutputException(MESSAGE_CANDIDATE_NOT_FOUND);
        }

        checkElectionVotes(candidate.getElectionId());

        candidate.setElectionId(candidateInput.getElectionId());
        candidate.setNumberElection(candidateInput.getNumberElection());
        candidate.setName(candidateInput.getName());
        candidate.setPartyId(candidateInput.getPartyId());
        candidate = candidateRepository.save(candidate);
        return modelMapper.map(candidate, CandidateOutput.class);
    }

    public GenericOutput delete(Long candidateId) {
        if (candidateId == null) {
            throw new GenericOutputException(MESSAGE_INVALID_ID);
        }

        Candidate candidate = candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            throw new GenericOutputException(MESSAGE_CANDIDATE_NOT_FOUND);
        }
        checkElectionVotes(candidate.getElectionId());
        candidateRepository.delete(candidate);

        return new GenericOutput("Candidate deleted");
    }

    private void checkElectionVotes(Long id) {
        try {
            Long totalVotes = electionClientService.getResultByElectionId(id).getTotalVotes();
            if (totalVotes > 0) {
                throw new GenericOutputException("This election already has votes");
            }
        } catch (FeignException e) {
            if (e.status() == 500) {
                throw new GenericOutputException("Invalid Election");
            }
        }
    }

    private void validateDuplicate(CandidateInput candidateInput, Long candidateId) {
        Candidate candidate = candidateRepository.findFirstByNumberElectionAndAndElectionId(candidateInput.getNumberElection(), candidateInput.getElectionId());
        if (candidate != null && !candidate.getId().equals(candidateId)) {
            throw new GenericOutputException("Duplicate Candidate!");
        }
    }

    private void validateInput(CandidateInput candidateInput) {
        if (StringUtils.isBlank(candidateInput.getName()) || candidateInput.getName().trim().length() < 5 || !candidateInput.getName().trim().contains(" ")) {
            throw new GenericOutputException("Invalid name");
        }
        if (candidateInput.getNumberElection() == null) {
            throw new GenericOutputException("Invalid Number Election");
        }
        if (candidateInput.getPartyId() == null) {
            throw new GenericOutputException("Invalid Party");
        }

        try {
            PartyOutput partyOutput = partyClientService.getById(candidateInput.getPartyId());
            if (!candidateInput.getNumberElection().toString().startsWith(partyOutput.getNumber().toString())) {
                throw new GenericOutputException("Number doesn't belong to party");
            }
        } catch (FeignException e) {
            if (e.status() == 500) {
                throw new GenericOutputException("Invalid Party");
            }
        }

        if (candidateInput.getElectionId() == null) {
            throw new GenericOutputException(MESSAGE_INVALID_ELECTION_ID);
        }
        try {
            electionClientService.getById(candidateInput.getElectionId());
        } catch (FeignException e) {
            if (e.status() == 500) {
                throw new GenericOutputException(MESSAGE_INVALID_ELECTION_ID);
            }
        }

    }

    public CandidateOutput toCandidateOutput(Candidate candidate) {
        CandidateOutput candidateOutput = modelMapper.map(candidate, CandidateOutput.class);
        ElectionOutput electionOutput = electionClientService.getById(candidate.getElectionId());
        candidateOutput.setElectionOutput(electionOutput);
        PartyOutput partyOutput = partyClientService.getById(candidate.getPartyId());
        candidateOutput.setPartyOutput(partyOutput);
        return candidateOutput;
    }

}
